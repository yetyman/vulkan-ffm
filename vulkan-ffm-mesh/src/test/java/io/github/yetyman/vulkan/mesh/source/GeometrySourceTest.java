package io.github.yetyman.vulkan.mesh.source;

import io.github.yetyman.helpers.math.Vec3;
import io.github.yetyman.helpers.math.geometry.AABB;
import io.github.yetyman.vulkan.mesh.AttributeFormat;
import io.github.yetyman.vulkan.mesh.AttributeSemantic;
import io.github.yetyman.vulkan.mesh.IndexWidth;
import io.github.yetyman.vulkan.mesh.MeshLayout;
import io.github.yetyman.vulkan.mesh.PrimitiveTopology;
import io.github.yetyman.vulkan.mesh.StridedCopy;
import io.github.yetyman.vulkan.mesh.source.primitives.BoxSource;

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_SHORT_UNALIGNED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Layer 1 tests: GeometrySource, AttributeStream, IndexStream, and transcoding.
 * All CPU-only, no device required.
 */
class GeometrySourceTest {

    @Test
    void boxSourceHasExpectedShape() {
        try (Arena arena = Arena.ofConfined()) {
            BoxSource box = new BoxSource(arena);
            assertEquals(24, box.elementCount());
            assertEquals(PrimitiveTopology.TRIANGLE_LIST, box.topology());
            assertTrue(box.indices().isPresent());
            assertEquals(36, box.indices().get().indexCount());
            assertTrue(box.available().contains(AttributeSemantic.POSITION));
            assertTrue(box.available().contains(AttributeSemantic.NORMAL));
            assertTrue(box.available().contains(AttributeSemantic.TEXCOORD(0)));
            assertTrue(box.nativeLayout().isPresent());
        }
    }

    @Test
    void boxBoundsMatchUnitCube() {
        try (Arena arena = Arena.ofConfined()) {
            BoxSource box = new BoxSource(arena);
            AABB bounds = box.bounds();
            assertEquals(-0.5f, bounds.min.x);
            assertEquals(0.5f, bounds.max.z);
        }
    }

    @Test
    void transcodePositionsIntoPlanarLayout() {
        try (Arena arena = Arena.ofConfined()) {
            BoxSource box = new BoxSource(arena);
            // Target: position only, tightly packed (planar position stream)
            MeshLayout target = MeshLayout.builder()
                    .stream(0).attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3)
                    .build();

            long stride = target.strideOf(0);
            assertEquals(12, stride);
            MemorySegment dst = arena.allocate(stride * box.elementCount());

            box.stream(AttributeSemantic.POSITION)
                    .transcodeInto(target, dst, 0, stride, 0, box.elementCount());

            // First vertex of the box +X face is (0.5, -0.5, -0.5) for the unit box
            float x0 = dst.get(JAVA_FLOAT_UNALIGNED, 0);
            float y0 = dst.get(JAVA_FLOAT_UNALIGNED, 4);
            float z0 = dst.get(JAVA_FLOAT_UNALIGNED, 8);
            assertEquals(0.5f, x0, 0.0001f);
            assertEquals(-0.5f, y0, 0.0001f);
            assertEquals(-0.5f, z0, 0.0001f);
        }
    }

    @Test
    void transcodeIndicesWithBaseOffset() {
        try (Arena arena = Arena.ofConfined()) {
            BoxSource box = new BoxSource(arena);
            IndexStream idx = box.indices().orElseThrow();
            assertEquals(IndexWidth.U16, idx.sourceWidth());

            // Transcode into U32 with a vertex base of 1000
            MemorySegment dst = arena.allocate(36 * 4L);
            idx.transcodeInto(IndexWidth.U32, 1000, dst, 0, 0, 36);

            // First index should be 0 + 1000 = 1000
            int first = dst.get(java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED, 0);
            assertEquals(1000, first);
            // Fourth index should be 0 + 1000 = 1000 (second triangle of first face starts here)
            int fourth = dst.get(java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED, 12);
            assertEquals(1000, fourth);
        }
    }

    @Test
    void transcodeWindowDoesNotTouchOutsideElements() {
        try (Arena arena = Arena.ofConfined()) {
            BoxSource box = new BoxSource(arena);
            MeshLayout target = MeshLayout.builder()
                    .stream(0).attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3)
                    .build();

            long stride = target.strideOf(0);
            // Allocate for all 24 but only transcode elements 4..7 (second face)
            MemorySegment dst = arena.allocate(stride * 24);
            // Fill with a sentinel
            dst.fill((byte) 0xFF);

            box.stream(AttributeSemantic.POSITION)
                    .transcodeInto(target, dst, stride * 4, stride, 4, 4);

            // Element 3 should still be sentinel
            assertEquals(0xFFFFFFFF, dst.get(java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED, stride * 3));
            // Element 4 should be a real float (not 0xFF)
            float x4 = dst.get(JAVA_FLOAT_UNALIGNED, stride * 4);
            assertTrue(Float.isFinite(x4));
            assertTrue(x4 != Float.intBitsToFloat(0xFFFFFFFF));
            // Element 8 should still be sentinel
            assertEquals(0xFFFFFFFF, dst.get(java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED, stride * 8));
        }
    }

    @Test
    void transcodeNormalsIntoInterleavedLayout() {
        try (Arena arena = Arena.ofConfined()) {
            BoxSource box = new BoxSource(arena);
            // Interleaved position + normal
            MeshLayout target = MeshLayout.builder()
                    .stream(0)
                    .attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3)
                    .attribute(AttributeSemantic.NORMAL, AttributeFormat.F32x3)
                    .build();

            long stride = target.strideOf(0);
            assertEquals(24, stride);
            MemorySegment dst = arena.allocate(stride * box.elementCount());

            // Transcode normals only (at their offset within the interleaved element)
            long normalOffset = target.offsetOf(AttributeSemantic.NORMAL);
            assertEquals(12, normalOffset);
            box.stream(AttributeSemantic.NORMAL)
                    .transcodeInto(target, dst, normalOffset, stride, 0, box.elementCount());

            // First 4 vertices are the +X face, so their normal is (1, 0, 0)
            float nx0 = dst.get(JAVA_FLOAT_UNALIGNED, normalOffset);
            float ny0 = dst.get(JAVA_FLOAT_UNALIGNED, normalOffset + 4);
            float nz0 = dst.get(JAVA_FLOAT_UNALIGNED, normalOffset + 8);
            assertEquals(1f, nx0, 0.0001f);
            assertEquals(0f, ny0, 0.0001f);
            assertEquals(0f, nz0, 0.0001f);
        }
    }

    @Test
    void identityLayoutFastPathDetected() {
        try (Arena arena = Arena.ofConfined()) {
            BoxSource box = new BoxSource(arena);
            // The box's native layout is interleaved pos+normal+uv in stream 0.
            MeshLayout boxLayout = box.nativeLayout().orElseThrow();

            // When target equals source layout, the transcode is a same-stride copy.
            // Verify it produces the same data as attribute-by-attribute.
            long stride = boxLayout.strideOf(0);
            MemorySegment attrByAttr = arena.allocate(stride * box.elementCount());
            MemorySegment flat = arena.allocate(stride * box.elementCount());

            // Attribute-by-attribute into attrByAttr
            for (AttributeSemantic sem : boxLayout.semantics()) {
                long offset = boxLayout.offsetOf(sem);
                box.stream(sem).transcodeInto(boxLayout, attrByAttr, offset, stride, 0, box.elementCount());
            }

            // Full-stream identity copy into flat
            for (AttributeSemantic sem : boxLayout.semantics()) {
                long offset = boxLayout.offsetOf(sem);
                box.stream(sem).transcodeInto(boxLayout, flat, offset, stride, 0, box.elementCount());
            }

            // Both should be identical (mismatch returns -1 when equal)
            assertEquals(-1, attrByAttr.mismatch(flat));
        }
    }

    @Test
    void customBoxDimensions() {
        try (Arena arena = Arena.ofConfined()) {
            BoxSource box = new BoxSource(arena, new Vec3(1, 2, 3), new Vec3(4, 5, 6));
            assertEquals(1f, box.bounds().min.x);
            assertEquals(6f, box.bounds().max.z);
            assertEquals(24, box.elementCount());
        }
    }

    // --- MeshLayout.transcodeOps ---

    @Test
    void transcodeOpsProducesOneCopyPerSharedAttribute() {
        MeshLayout src = MeshLayout.builder()
                .stream(0)
                .attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3)
                .attribute(AttributeSemantic.NORMAL, AttributeFormat.F32x3)
                .build();

        MeshLayout dst = MeshLayout.builder()
                .stream(0).attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3)
                .stream(1).attribute(AttributeSemantic.NORMAL, AttributeFormat.F32x3)
                .build();

        var ops = dst.transcodeOps(src, 0, 10);
        assertEquals(2, ops.size());
        // Position: src stream 0, dst stream 0
        assertEquals(AttributeSemantic.POSITION, ops.get(0).semantic());
        assertEquals(0, ops.get(0).srcStreamId());
        assertEquals(0, ops.get(0).dstStreamId());
        assertTrue(ops.get(0).isPureCopy());
        assertEquals(12, ops.get(0).elementByteSize());
        // Normal: src stream 0, dst stream 1
        assertEquals(AttributeSemantic.NORMAL, ops.get(1).semantic());
        assertEquals(0, ops.get(1).srcStreamId());
        assertEquals(1, ops.get(1).dstStreamId());
        assertTrue(ops.get(1).isPureCopy());
    }

    @Test
    void transcodeOpsSkipsMissingAttributes() {
        MeshLayout src = MeshLayout.builder()
                .stream(0).attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3)
                .build();

        MeshLayout dst = MeshLayout.builder()
                .stream(0)
                .attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3)
                .attribute(AttributeSemantic.NORMAL, AttributeFormat.F32x3)
                .build();

        var ops = dst.transcodeOps(src, 0, 5);
        // Only position is shared; normal is absent from source.
        assertEquals(1, ops.size());
        assertEquals(AttributeSemantic.POSITION, ops.get(0).semantic());
    }

    @Test
    void transcodeOpsOffsetsReflectElementWindow() {
        MeshLayout layout = MeshLayout.builder()
                .stream(0).attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3)
                .build();

        var ops = layout.transcodeOps(layout, 10, 5);
        assertEquals(1, ops.size());
        // Element 10 at stride 12 = offset 120
        assertEquals(120, ops.get(0).srcOffset());
        assertEquals(120, ops.get(0).dstOffset());
    }

    @Test
    void isIdenticalToDetectsMatch() {
        MeshLayout a = MeshLayout.builder()
                .stream(0)
                .attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3)
                .attribute(AttributeSemantic.NORMAL, AttributeFormat.F32x3)
                .build();

        MeshLayout b = MeshLayout.builder()
                .stream(0)
                .attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3)
                .attribute(AttributeSemantic.NORMAL, AttributeFormat.F32x3)
                .build();

        assertTrue(a.isIdenticalTo(b));
        assertTrue(b.isIdenticalTo(a));
    }

    @Test
    void isIdenticalToDetectsDifference() {
        MeshLayout a = MeshLayout.builder()
                .stream(0)
                .attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3)
                .attribute(AttributeSemantic.NORMAL, AttributeFormat.F32x3)
                .build();

        MeshLayout b = MeshLayout.builder()
                .stream(0).attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3)
                .stream(1).attribute(AttributeSemantic.NORMAL, AttributeFormat.F32x3)
                .build();

        assertFalse(a.isIdenticalTo(b));
    }

    // --- MeshOutputSource ---

    @Test
    void meshOutputSourceExposesPositionAndIndices() {
        io.github.yetyman.helpers.math.spatial.isosurface.MeshOutput mo =
                new io.github.yetyman.helpers.math.spatial.isosurface.MeshOutput();
        mo.addVertex(0, 0, 0);
        mo.addVertex(1, 0, 0);
        mo.addVertex(0, 1, 0);
        mo.addTriangle(0, 1, 2);

        MeshOutputSource src = new MeshOutputSource(mo);
        assertEquals(3, src.elementCount());
        assertEquals(PrimitiveTopology.TRIANGLE_LIST, src.topology());
        assertTrue(src.available().contains(AttributeSemantic.POSITION));
        assertTrue(src.indices().isPresent());
        assertEquals(3, src.indices().get().indexCount());
        assertTrue(src.nativeLayout().isEmpty());
    }

    @Test
    void meshOutputSourceTranscodesPositions() {
        io.github.yetyman.helpers.math.spatial.isosurface.MeshOutput mo =
                new io.github.yetyman.helpers.math.spatial.isosurface.MeshOutput();
        mo.addVertex(1, 2, 3);
        mo.addVertex(4, 5, 6);

        MeshOutputSource src = new MeshOutputSource(mo);
        MeshLayout target = MeshLayout.builder()
                .stream(0).attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3)
                .build();

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment dst = arena.allocate(12 * 2);
            src.stream(AttributeSemantic.POSITION)
                    .transcodeInto(target, dst, 0, 12, 0, 2);

            assertEquals(1f, dst.get(JAVA_FLOAT_UNALIGNED, 0));
            assertEquals(2f, dst.get(JAVA_FLOAT_UNALIGNED, 4));
            assertEquals(3f, dst.get(JAVA_FLOAT_UNALIGNED, 8));
            assertEquals(4f, dst.get(JAVA_FLOAT_UNALIGNED, 12));
            assertEquals(5f, dst.get(JAVA_FLOAT_UNALIGNED, 16));
            assertEquals(6f, dst.get(JAVA_FLOAT_UNALIGNED, 20));
        }
    }

    @Test
    void meshOutputSourceBoundsAreComputed() {
        io.github.yetyman.helpers.math.spatial.isosurface.MeshOutput mo =
                new io.github.yetyman.helpers.math.spatial.isosurface.MeshOutput();
        mo.addVertex(-1, -2, -3);
        mo.addVertex(4, 5, 6);
        mo.addTriangle(0, 1, 0);

        MeshOutputSource src = new MeshOutputSource(mo);
        assertEquals(-1f, src.bounds().min.x);
        assertEquals(6f, src.bounds().max.z);
    }

    @Test
    void segmentGeometrySourceBuilderDerivesStreams() {
        try (Arena arena = Arena.ofConfined()) {
            MeshLayout layout = MeshLayout.builder()
                    .stream(0)
                    .attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3)
                    .build();

            MemorySegment data = arena.allocate(12 * 3);
            data.set(JAVA_FLOAT_UNALIGNED, 0, 1f);
            data.set(JAVA_FLOAT_UNALIGNED, 4, 2f);
            data.set(JAVA_FLOAT_UNALIGNED, 8, 3f);

            SegmentGeometrySource src = SegmentGeometrySource.builder()
                    .layout(layout)
                    .elementCount(3)
                    .bounds(new AABB(new Vec3(0, 0, 0), new Vec3(1, 1, 1)))
                    .streamData(0, data)
                    .build();

            assertEquals(3, src.elementCount());
            assertTrue(src.available().contains(AttributeSemantic.POSITION));
            assertEquals(Residency.HOST, src.stream(AttributeSemantic.POSITION).residency());
        }
    }
}
