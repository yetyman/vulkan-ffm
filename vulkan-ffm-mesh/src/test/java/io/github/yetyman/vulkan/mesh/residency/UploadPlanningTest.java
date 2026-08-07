package io.github.yetyman.vulkan.mesh.residency;

import io.github.yetyman.helpers.math.Vec3;
import io.github.yetyman.helpers.math.geometry.AABB;
import io.github.yetyman.vulkan.mesh.AttributeFormat;
import io.github.yetyman.vulkan.mesh.AttributeSemantic;
import io.github.yetyman.vulkan.mesh.DeviceRange;
import io.github.yetyman.vulkan.mesh.ElementWindow;
import io.github.yetyman.vulkan.mesh.IndexWidth;
import io.github.yetyman.vulkan.mesh.MeshLayout;
import io.github.yetyman.vulkan.mesh.source.primitives.BoxSource;

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for upload planning: per-stream grouping, identity fast path, and partial updates.
 *
 * <p>The grouping behaviour is the one the earlier per-attribute design got wrong, and the bug was
 * invisible on the GPU until three meshes rendered as garbage. These assertions pin it.
 */
class UploadPlanningTest {

    private static final MeshLayout INTERLEAVED = MeshLayout.builder()
            .stream(0)
            .attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3)
            .attribute(AttributeSemantic.NORMAL, AttributeFormat.F32x3)
            .attribute(AttributeSemantic.TEXCOORD(0), AttributeFormat.F32x2)
            .build();

    /** Position alone in stream 0, the rest interleaved in stream 1. */
    private static final MeshLayout HYBRID = MeshLayout.builder()
            .stream(0).attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3)
            .stream(1)
            .attribute(AttributeSemantic.NORMAL, AttributeFormat.F32x3)
            .attribute(AttributeSemantic.TEXCOORD(0), AttributeFormat.F32x2)
            .build();

    // --- Grouping ---

    @Test
    void interleavedStreamProducesOneGroupedTranscodeNotThreeSeparateOnes() {
        try (Arena arena = Arena.ofConfined()) {
            BoxSource box = new BoxSource(arena);
            var alloc = new StubAllocator();
            var allocation = alloc.allocate(INTERLEAVED, box.elementCount(), IndexWidth.U16, 36);

            // The box's native layout is this exact interleaved layout, and the box exposes raw
            // segments, so the identity fast path applies: one flat copy, not per-attribute work.
            UploadPlan plan = new UploadPlanner().plan(box, INTERLEAVED, allocation, alloc);

            long streamOps = plan.ops().stream()
                    .filter(o -> o instanceof UploadOp.HostCopy || o instanceof UploadOp.TranscodeStream)
                    .count();
            assertEquals(1, streamOps, "one op per stream, never one per attribute");
        }
    }

    @Test
    void nonIdentityLayoutGroupsAllAttributesOfAStreamIntoOneOp() {
        try (Arena arena = Arena.ofConfined()) {
            BoxSource box = new BoxSource(arena);
            var alloc = new StubAllocator();
            // HYBRID differs from the box's native layout, so the identity path cannot be used
            // and real transcoding happens.
            var allocation = alloc.allocate(HYBRID, box.elementCount(), IndexWidth.U16, 36);
            UploadPlan plan = new UploadPlanner().plan(box, HYBRID, allocation, alloc);

            var transcodes = plan.ops().stream()
                    .filter(o -> o instanceof UploadOp.TranscodeStream)
                    .map(o -> (UploadOp.TranscodeStream) o)
                    .toList();

            assertEquals(2, transcodes.size(), "two streams, so two ops");
            for (var ts : transcodes) {
                assertTrue(ts.coversAllAttributes(),
                        "a full upload must cover every attribute of its stream");
            }
            // Stream 1 carries two attributes, and they must be in the same op.
            var stream1 = transcodes.stream().filter(t -> t.streamId() == 1).findFirst().orElseThrow();
            assertEquals(2, stream1.attributes().size(),
                    "normal and uv share stream 1 and must be written together");
        }
    }

    @Test
    void attributeOffsetsWithinGroupedOpMatchTheLayout() {
        try (Arena arena = Arena.ofConfined()) {
            BoxSource box = new BoxSource(arena);
            var alloc = new StubAllocator();
            var allocation = alloc.allocate(HYBRID, box.elementCount(), IndexWidth.U16, 36);
            UploadPlan plan = new UploadPlanner().plan(box, HYBRID, allocation, alloc);

            var stream1 = plan.ops().stream()
                    .filter(o -> o instanceof UploadOp.TranscodeStream t && t.streamId() == 1)
                    .map(o -> (UploadOp.TranscodeStream) o)
                    .findFirst().orElseThrow();

            assertEquals(20, stream1.dstStride(), "normal(12) + uv(8)");
            for (var attr : stream1.attributes()) {
                long expected = HYBRID.offsetOf(attr.semantic());
                assertEquals(expected, attr.offsetInElement());
            }
        }
    }

    // --- Partial updates ---

    @Test
    void partialUpdateOfASharedStreamIsMarkedAsNotCoveringAll() {
        try (Arena arena = Arena.ofConfined()) {
            BoxSource box = new BoxSource(arena);
            var alloc = new StubAllocator();
            var allocation = alloc.allocate(INTERLEAVED, box.elementCount(), IndexWidth.U16, 36);

            // Update only normals, which share stream 0 with position and uv.
            UploadPlan plan = new UploadPlanner().planUpdate(box, INTERLEAVED, allocation,
                    Set.of(AttributeSemantic.NORMAL), ElementWindow.all(24));

            var ts = plan.ops().stream()
                    .filter(o -> o instanceof UploadOp.TranscodeStream)
                    .map(o -> (UploadOp.TranscodeStream) o)
                    .findFirst().orElseThrow();

            assertEquals(1, ts.attributes().size());
            assertFalse(ts.coversAllAttributes(),
                    "partial coverage must be flagged so the executor preserves neighbours");
        }
    }

    @Test
    void updateOfADedicatedStreamCoversAllAndStaysOnTheFastPath() {
        try (Arena arena = Arena.ofConfined()) {
            BoxSource box = new BoxSource(arena);
            var alloc = new StubAllocator();
            var allocation = alloc.allocate(HYBRID, box.elementCount(), IndexWidth.U16, 36);

            // Position has stream 0 to itself, so updating it covers that stream completely.
            UploadPlan plan = new UploadPlanner().planUpdate(box, HYBRID, allocation,
                    Set.of(AttributeSemantic.POSITION), ElementWindow.all(24));

            var ts = plan.ops().stream()
                    .filter(o -> o instanceof UploadOp.TranscodeStream)
                    .map(o -> (UploadOp.TranscodeStream) o)
                    .findFirst().orElseThrow();

            assertEquals(0, ts.streamId());
            assertTrue(ts.coversAllAttributes(),
                    "a dedicated stream update needs no read-modify-write");
        }
    }

    @Test
    void partialUpdateTouchesOnlyTheStreamsItNeeds() {
        try (Arena arena = Arena.ofConfined()) {
            BoxSource box = new BoxSource(arena);
            var alloc = new StubAllocator();
            var allocation = alloc.allocate(HYBRID, box.elementCount(), IndexWidth.U16, 36);

            UploadPlan plan = new UploadPlanner().planUpdate(box, HYBRID, allocation,
                    Set.of(AttributeSemantic.POSITION), ElementWindow.all(24));

            assertEquals(1, plan.ops().size(), "only stream 0 is touched, and no indices");
        }
    }

    @Test
    void updateWindowIsCarriedThrough() {
        try (Arena arena = Arena.ofConfined()) {
            BoxSource box = new BoxSource(arena);
            var alloc = new StubAllocator();
            var allocation = alloc.allocate(HYBRID, box.elementCount(), IndexWidth.U16, 36);

            UploadPlan plan = new UploadPlanner().planUpdate(box, HYBRID, allocation,
                    Set.of(AttributeSemantic.POSITION), new ElementWindow(4, 8));

            var ts = plan.ops().stream()
                    .filter(o -> o instanceof UploadOp.TranscodeStream)
                    .map(o -> (UploadOp.TranscodeStream) o)
                    .findFirst().orElseThrow();

            assertEquals(4, ts.firstElement());
            assertEquals(8, ts.elementCount());
        }
    }

    @Test
    void emptyUpdateProducesNoOps() {
        try (Arena arena = Arena.ofConfined()) {
            BoxSource box = new BoxSource(arena);
            var alloc = new StubAllocator();
            var allocation = alloc.allocate(HYBRID, box.elementCount(), IndexWidth.U16, 36);

            assertTrue(new UploadPlanner().planUpdate(box, HYBRID, allocation,
                    Set.of(AttributeSemantic.POSITION), ElementWindow.empty()).ops().isEmpty());
            assertTrue(new UploadPlanner().planUpdate(box, HYBRID, allocation,
                    Set.of(), ElementWindow.all(24)).ops().isEmpty());
        }
    }

    // --- Windowed full upload ---

    @Test
    void partialWindowUploadOmitsIndices() {
        try (Arena arena = Arena.ofConfined()) {
            BoxSource box = new BoxSource(arena);
            var alloc = new StubAllocator();
            var allocation = alloc.allocate(INTERLEAVED, box.elementCount(), IndexWidth.U16, 36);

            UploadPlan plan = new UploadPlanner().plan(box, INTERLEAVED, allocation, alloc,
                    new ElementWindow(0, 12));

            boolean hasIndices = plan.ops().stream().anyMatch(o -> o instanceof UploadOp.TranscodeIndices);
            assertFalse(hasIndices, "a partial vertex window says nothing about indices");
        }
    }

    @Test
    void fullWindowUploadIncludesIndices() {
        try (Arena arena = Arena.ofConfined()) {
            BoxSource box = new BoxSource(arena);
            var alloc = new StubAllocator();
            var allocation = alloc.allocate(INTERLEAVED, box.elementCount(), IndexWidth.U16, 36);

            UploadPlan plan = new UploadPlanner().plan(box, INTERLEAVED, allocation, alloc);

            var idx = plan.ops().stream()
                    .filter(o -> o instanceof UploadOp.TranscodeIndices)
                    .map(o -> (UploadOp.TranscodeIndices) o)
                    .findFirst().orElseThrow();
            assertEquals(IndexWidth.U16, idx.targetWidth(), "source width is preserved, not narrowed");
            assertEquals(36, idx.indexCount());
        }
    }

    @Test
    void indexUpdateCanBePlannedAlone() {
        try (Arena arena = Arena.ofConfined()) {
            BoxSource box = new BoxSource(arena);
            var alloc = new StubAllocator();
            var allocation = alloc.allocate(INTERLEAVED, box.elementCount(), IndexWidth.U16, 36);

            UploadPlan plan = new UploadPlanner()
                    .planIndexUpdate(box, allocation, alloc, new ElementWindow(6, 6));

            assertEquals(1, plan.ops().size());
            var idx = (UploadOp.TranscodeIndices) plan.ops().get(0);
            assertEquals(6, idx.firstIndex());
            assertEquals(6, idx.indexCount());
            // Offset advances by the index width, not the element stride.
            assertEquals(6 * 2, idx.dstOffset());
        }
    }

    // --- ElementWindow ---

    @Test
    void windowUnionCoalescesDisjointEdits() {
        ElementWindow a = new ElementWindow(2, 3);   // [2, 5)
        ElementWindow b = new ElementWindow(10, 2);  // [10, 12)
        ElementWindow u = a.union(b);
        assertEquals(2, u.firstElement());
        assertEquals(10, u.elementCount()); // [2, 12)
    }

    @Test
    void windowUnionWithEmptyIsIdentity() {
        ElementWindow a = new ElementWindow(4, 6);
        assertEquals(a, a.union(ElementWindow.empty()));
        assertEquals(a, ElementWindow.empty().union(a));
    }

    @Test
    void windowClampRespectsLimit() {
        assertEquals(new ElementWindow(8, 2), new ElementWindow(8, 10).clampTo(10));
        assertTrue(new ElementWindow(12, 4).clampTo(10).isEmpty());
    }

    // -------------------------------------------------------------------------

    /** Minimal allocator over heap-backed buffers, so planning is testable without a device. */
    private static final class StubAllocator implements GeometryAllocator {
        @Override
        public GeometryAllocation allocate(MeshLayout layout, long vertexCapacity,
                                           IndexWidth indexWidth, long indexCapacity) {
            int streams = layout.streamCount();
            DeviceRange[] v = new DeviceRange[streams];
            for (int s = 0; s < streams; s++) {
                long stride = layout.strideOf(s);
                v[s] = new DeviceRange(new HeapBuffer(stride * Math.max(vertexCapacity, 1)),
                        0, stride * vertexCapacity, stride);
            }
            DeviceRange idx = null;
            if (indexWidth != null && indexCapacity > 0) {
                long size = (long) indexWidth.byteSize() * indexCapacity;
                idx = new DeviceRange(new HeapBuffer(size), 0, size, indexWidth.byteSize());
            }
            final DeviceRange[] fv = v;
            final DeviceRange fi = idx;
            return new GeometryAllocation() {
                @Override public DeviceRange vertexRange(int streamId) { return fv[streamId]; }
                @Override public Optional<DeviceRange> indexRange() { return Optional.ofNullable(fi); }
                @Override public long vertexBase() { return 0; }
                @Override public long indexBase() { return 0; }
            };
        }

        @Override public void free(GeometryAllocation allocation) {}
        @Override public IndexBaseMode indexBaseMode() { return IndexBaseMode.RELATIVE_WITH_DRAW_OFFSET; }
        @Override public void close() {}
    }

    private static final class HeapBuffer implements io.github.yetyman.vulkan.buffers.IBuffer {
        private final java.lang.foreign.MemorySegment seg;
        HeapBuffer(long size) { this.seg = java.lang.foreign.Arena.ofAuto().allocate(Math.max(size, 1)); }
        @Override public java.lang.foreign.MemorySegment handle() { return seg; }
        @Override public long size() { return seg.byteSize(); }
        @Override public io.github.yetyman.vulkan.buffers.BufferUsage usage() {
            return io.github.yetyman.vulkan.buffers.BufferUsage.STORAGE;
        }
        @Override public void write(java.nio.ByteBuffer d, long o, io.github.yetyman.vulkan.VkQueue q) {}
        @Override public io.github.yetyman.vulkan.buffers.GpuCompletion writeAsync(
                java.nio.ByteBuffer d, long o, io.github.yetyman.vulkan.VkQueue q) {
            return io.github.yetyman.vulkan.buffers.GpuCompletion.completed();
        }
        @Override public io.github.yetyman.vulkan.buffers.BufferWriteScope acquireWrite(
                long offset, long size, io.github.yetyman.vulkan.VkQueue q) {
            return io.github.yetyman.vulkan.buffers.BufferWriteScope.of(
                    seg.asSlice(offset, size), offset, size, null);
        }
        @Override public io.github.yetyman.vulkan.buffers.BufferReadScope acquireRead(
                long offset, long size, io.github.yetyman.vulkan.VkQueue q) {
            return io.github.yetyman.vulkan.buffers.BufferReadScope.of(
                    seg.asSlice(offset, size), offset, size, null);
        }
        @Override public java.nio.ByteBuffer read(long o, long s) { return seg.asSlice(o, s).asByteBuffer(); }
        @Override public void flush() {}
        @Override public void copyTo(io.github.yetyman.vulkan.buffers.IBuffer d, long so, long dof,
                                     long l, io.github.yetyman.vulkan.VkQueue q) {}
        @Override public io.github.yetyman.vulkan.buffers.GpuCompletion copyToAsync(
                io.github.yetyman.vulkan.buffers.IBuffer d, long so, long dof, long l,
                io.github.yetyman.vulkan.VkQueue q) {
            return io.github.yetyman.vulkan.buffers.GpuCompletion.completed();
        }
        @Override public void close() {}
    }
}
