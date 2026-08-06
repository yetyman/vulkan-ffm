package io.github.yetyman.vulkan.mesh.consume;

import io.github.yetyman.helpers.math.Vec3;
import io.github.yetyman.helpers.math.geometry.AABB;
import io.github.yetyman.vulkan.mesh.AttributeFormat;
import io.github.yetyman.vulkan.mesh.AttributeSemantic;
import io.github.yetyman.vulkan.mesh.MeshLayout;
import io.github.yetyman.vulkan.mesh.PrimitiveTopology;
import io.github.yetyman.vulkan.mesh.partition.GeometryPartition;
import io.github.yetyman.vulkan.mesh.partition.PartitionSet;
import io.github.yetyman.vulkan.mesh.partition.SinglePartition;
import io.github.yetyman.vulkan.mesh.source.primitives.BoxSource;

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for Layer 2 (partitioning) and Layer 4 (consumption) types. CPU-only, no device.
 */
class ConsumptionTest {

    // --- GeometryDrawRange ---

    @Test
    void indexedDrawRangeHasExpectedFields() {
        GeometryDrawRange r = GeometryDrawRange.indexed(36, 0, 100, PrimitiveTopology.TRIANGLE_LIST);
        assertEquals(36, r.indexCount());
        assertEquals(1, r.instanceCount());
        assertEquals(0, r.firstIndex());
        assertEquals(100, r.vertexOffset());
        assertEquals(0, r.firstInstance());
        assertTrue(r.indexed());
        assertEquals(PrimitiveTopology.TRIANGLE_LIST, r.topology());
    }

    @Test
    void nonIndexedDrawRange() {
        GeometryDrawRange r = GeometryDrawRange.nonIndexed(24, 0, PrimitiveTopology.TRIANGLE_LIST);
        assertEquals(24, r.indexCount());
        assertFalse(r.indexed());
        assertEquals(0, r.vertexOffset());
    }

    // --- IndirectDrawEncoder ---

    @Test
    void encodedIndexedCommandMatchesVulkanLayout() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment dst = arena.allocate(IndirectDrawEncoder.INDEXED_STRIDE * 2);

            IndirectDrawEncoder.encodeIndexed(dst, 0, 36, 1, 0, 100, 0);
            IndirectDrawEncoder.encodeIndexed(dst, 1, 12, 5, 6, 200, 3);

            // Command 0
            assertEquals(36, dst.get(JAVA_INT_UNALIGNED, 0));
            assertEquals(1, dst.get(JAVA_INT_UNALIGNED, 4));
            assertEquals(0, dst.get(JAVA_INT_UNALIGNED, 8));
            assertEquals(100, dst.get(JAVA_INT_UNALIGNED, 12));
            assertEquals(0, dst.get(JAVA_INT_UNALIGNED, 16));

            // Command 1
            long o = IndirectDrawEncoder.INDEXED_STRIDE;
            assertEquals(12, dst.get(JAVA_INT_UNALIGNED, o));
            assertEquals(5, dst.get(JAVA_INT_UNALIGNED, o + 4));
            assertEquals(6, dst.get(JAVA_INT_UNALIGNED, o + 8));
            assertEquals(200, dst.get(JAVA_INT_UNALIGNED, o + 12));
            assertEquals(3, dst.get(JAVA_INT_UNALIGNED, o + 16));
        }
    }

    @Test
    void encodeRecordFormDelegatesToPrimitive() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment a = arena.allocate(IndirectDrawEncoder.INDEXED_STRIDE);
            MemorySegment b = arena.allocate(IndirectDrawEncoder.INDEXED_STRIDE);

            GeometryDrawRange range = GeometryDrawRange.indexed(36, 0, 100, PrimitiveTopology.TRIANGLE_LIST);
            IndirectDrawEncoder.encode(a, 0, range);
            IndirectDrawEncoder.encodeIndexed(b, 0, 36, 1, 0, 100, 0);

            assertEquals(-1, a.mismatch(b));
        }
    }

    @Test
    void encodeMeshTaskCommand() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment dst = arena.allocate(IndirectDrawEncoder.MESH_TASK_STRIDE);
            IndirectDrawEncoder.encodeMeshTask(dst, 0, 4, 2, 1);
            assertEquals(4, dst.get(JAVA_INT_UNALIGNED, 0));
            assertEquals(2, dst.get(JAVA_INT_UNALIGNED, 4));
            assertEquals(1, dst.get(JAVA_INT_UNALIGNED, 8));
        }
    }

    // --- GeometryPartition ---

    @Test
    void partitionIndexCountDerivedFromTopology() {
        AABB bounds = new AABB(new Vec3(0, 0, 0), new Vec3(1, 1, 1));
        GeometryPartition p = new GeometryPartition("face0", 0, 12, 24,
                PrimitiveTopology.TRIANGLE_LIST, bounds, 42, 100);
        assertEquals(36, p.indexCount()); // 12 triangles * 3
        assertTrue(p.isIndexed());
        assertEquals(42, p.tag());
        assertEquals(100, p.sortKey());
    }

    // --- PartitionSet ---

    @Test
    void singlePartitionSetHasOneMember() {
        AABB bounds = new AABB(new Vec3(-1, -1, -1), new Vec3(1, 1, 1));
        GeometryPartition p = new GeometryPartition("whole", 0, 4, 12,
                PrimitiveTopology.TRIANGLE_LIST, bounds, 0, 0);
        PartitionSet ps = PartitionSet.single(p);
        assertEquals(1, ps.count());
        assertEquals(p, ps.get(0));
        assertEquals(bounds, ps.bounds());
        assertFalse(ps.hierarchy().isPresent());
    }

    // --- SinglePartition strategy ---

    @Test
    void singlePartitionStrategyCoversWholeSource() {
        try (Arena arena = Arena.ofConfined()) {
            BoxSource box = new BoxSource(arena);
            PartitionSet ps = SinglePartition.INSTANCE.partition(box);
            assertEquals(1, ps.count());
            assertEquals(12, ps.get(0).primitiveCount()); // 36 indices / 3
            assertEquals(24, ps.get(0).vertexCount());
        }
    }
}
