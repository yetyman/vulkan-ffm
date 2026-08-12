package io.github.yetyman.vulkan.mesh.partition;

import io.github.yetyman.helpers.math.Vec3;
import io.github.yetyman.helpers.math.geometry.AABB;
import io.github.yetyman.vulkan.mesh.AttributeFormat;
import io.github.yetyman.vulkan.mesh.AttributeSemantic;
import io.github.yetyman.vulkan.mesh.IndexWidth;
import io.github.yetyman.vulkan.mesh.MeshLayout;
import io.github.yetyman.vulkan.mesh.PrimitiveTopology;
import io.github.yetyman.vulkan.mesh.source.AttributeStream;
import io.github.yetyman.vulkan.mesh.source.GeometrySource;
import io.github.yetyman.vulkan.mesh.source.IndexStream;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongUnaryOperator;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_SHORT_UNALIGNED;

/**
 * Partitions geometry by evaluating a per-primitive tag function. Each unique tag value becomes
 * its own {@link GeometryPartition}. Primitives with the same tag are grouped into a contiguous
 * range (by reordering indices/vertices conceptually -- the partitions refer to index ranges
 * in the original order since this is a read-only partitioning).
 *
 * <p>This strategy requires the source to be indexed and host-readable, and requires a
 * triangle-list topology (each primitive consumes 3 indices). Extensions to other fixed-count
 * topologies are straightforward but deferred until needed.</p>
 *
 * <p>The tag function maps a primitive index (0-based) to a tag value. Partition names are
 * derived from the tag; if a name function is provided, it maps tags to diagnostic names.</p>
 *
 * <p>Example usage: partitioning by material ID stored in a per-face attribute, or by spatial
 * region (octant), or by any computed criterion.</p>
 */
public final class TagPartitioning implements PartitioningStrategy {

    private final LongUnaryOperator tagFunction;
    private final LongNameFunction nameFunction;

    /**
     * Maps a tag value to a diagnostic partition name.
     */
    @FunctionalInterface
    public interface LongNameFunction {
        String name(long tag);
    }

    /**
     * @param tagFunction  maps primitive index to tag value
     * @param nameFunction optional: maps tag to a diagnostic name. May be null.
     */
    public TagPartitioning(LongUnaryOperator tagFunction, LongNameFunction nameFunction) {
        if (tagFunction == null) throw new IllegalArgumentException("tagFunction required");
        this.tagFunction = tagFunction;
        this.nameFunction = nameFunction;
    }

    /**
     * Creates a tag partitioning with no custom names (tags are stringified as their long value).
     */
    public TagPartitioning(LongUnaryOperator tagFunction) {
        this(tagFunction, null);
    }

    @Override
    public PartitionSet partition(GeometrySource source) {
        if (source.indices().isEmpty()) {
            throw new IllegalArgumentException(
                    "TagPartitioning requires an indexed source");
        }
        IndexStream idxStream = source.indices().get();
        if (!idxStream.isHostReadable()) {
            throw new IllegalStateException(
                    "TagPartitioning requires host-readable indices");
        }

        PrimitiveTopology topology = source.topology();
        int ipp = topology.indicesPerPrimitive();
        if (ipp <= 0) {
            throw new IllegalArgumentException(
                    "TagPartitioning requires a topology with fixed indices-per-primitive, got: "
                    + topology.name());
        }

        long indexCount = idxStream.indexCount();
        long primitiveCount = indexCount / ipp;

        // Evaluate tags for all primitives and group them
        // LinkedHashMap preserves insertion order so partitions are stable
        Map<Long, List<Long>> tagToPrimitives = new LinkedHashMap<>();
        for (long p = 0; p < primitiveCount; p++) {
            long tag = tagFunction.applyAsLong(p);
            tagToPrimitives.computeIfAbsent(tag, k -> new ArrayList<>()).add(p);
        }

        // We need access to position data for bounds computation
        AttributeStream posStream = null;
        boolean hasBoundsData = source.available().contains(AttributeSemantic.POSITION);
        MemorySegment posData = null;
        long posStride = 0;
        long posOffset = 0;

        // Read index data for bounds computation
        MemorySegment indexData = null;
        if (hasBoundsData) {
            posStream = source.stream(AttributeSemantic.POSITION);
            if (posStream.isHostReadable()) {
                // Read all indices into a temporary buffer for bounds computation
                try (Arena tempArena = Arena.ofConfined()) {
                    indexData = tempArena.allocate(indexCount * 4L);
                    idxStream.transcodeInto(IndexWidth.U32, 0, indexData, 0, 0, indexCount);

                    // Read all positions into a temp buffer
                    MeshLayout posLayout = MeshLayout.builder()
                            .stream(0).attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3)
                            .build();
                    posStride = posLayout.strideOf(0);
                    long vertexCount = source.elementCount();
                    posData = tempArena.allocate(posStride * vertexCount);
                    posStream.transcodeInto(posLayout, posData, 0, posStride, 0, vertexCount);

                    // Build partitions with computed bounds
                    List<GeometryPartition> parts = buildPartitionsWithBounds(
                            tagToPrimitives, topology, ipp, indexData, posData, posStride);
                    return PartitionSet.of(parts);
                }
            }
        }

        // Fallback: no position data available, use source bounds for all partitions
        List<GeometryPartition> parts = buildPartitionsWithoutBounds(
                tagToPrimitives, topology, ipp, source.bounds());
        return PartitionSet.of(parts);
    }

    private List<GeometryPartition> buildPartitionsWithBounds(
            Map<Long, List<Long>> tagToPrimitives, PrimitiveTopology topology, int ipp,
            MemorySegment indexData, MemorySegment posData, long posStride) {

        List<GeometryPartition> parts = new ArrayList<>();

        for (Map.Entry<Long, List<Long>> entry : tagToPrimitives.entrySet()) {
            long tag = entry.getKey();
            List<Long> primitives = entry.getValue();

            // Compute bounds for this partition's primitives
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

            for (long prim : primitives) {
                for (int v = 0; v < ipp; v++) {
                    long idxOffset = (prim * ipp + v) * 4L;
                    int vertexIndex = indexData.get(JAVA_INT_UNALIGNED, idxOffset);
                    long posBase = vertexIndex * posStride;
                    float px = posData.get(JAVA_FLOAT_UNALIGNED, posBase);
                    float py = posData.get(JAVA_FLOAT_UNALIGNED, posBase + 4);
                    float pz = posData.get(JAVA_FLOAT_UNALIGNED, posBase + 8);
                    minX = Math.min(minX, px); minY = Math.min(minY, py); minZ = Math.min(minZ, pz);
                    maxX = Math.max(maxX, px); maxY = Math.max(maxY, py); maxZ = Math.max(maxZ, pz);
                }
            }

            AABB bounds = new AABB(new Vec3(minX, minY, minZ), new Vec3(maxX, maxY, maxZ));

            // The partition refers to indices in the original order. firstIndex is the first
            // primitive's first index, and primitiveCount is the number of primitives in this tag.
            // Note: this is a logical grouping -- the indices are not physically contiguous in the
            // source unless they happen to be. The partition's firstIndex points to the first
            // primitive of this group; consumers that need contiguous ranges must reorder.
            long firstPrim = primitives.getFirst();
            long firstIndex = firstPrim * ipp;

            String name = nameFunction != null ? nameFunction.name(tag) : String.valueOf(tag);

            // Count unique vertices referenced by this partition's primitives
            long vertexCount = countUniqueVertices(primitives, ipp, indexData);

            parts.add(new GeometryPartition(
                    name, firstIndex, primitives.size(), vertexCount,
                    topology, bounds, tag, tag));
        }

        return parts;
    }

    private List<GeometryPartition> buildPartitionsWithoutBounds(
            Map<Long, List<Long>> tagToPrimitives, PrimitiveTopology topology, int ipp,
            AABB sourceBounds) {

        List<GeometryPartition> parts = new ArrayList<>();

        for (Map.Entry<Long, List<Long>> entry : tagToPrimitives.entrySet()) {
            long tag = entry.getKey();
            List<Long> primitives = entry.getValue();
            long firstPrim = primitives.getFirst();
            long firstIndex = firstPrim * ipp;

            String name = nameFunction != null ? nameFunction.name(tag) : String.valueOf(tag);

            parts.add(new GeometryPartition(
                    name, firstIndex, primitives.size(), 0,
                    topology, sourceBounds, tag, tag));
        }

        return parts;
    }

    private long countUniqueVertices(List<Long> primitives, int ipp, MemorySegment indexData) {
        // Use a simple approach: find min and max vertex index to estimate range.
        // For an exact count we would need a BitSet or HashSet, which is expensive for large meshes.
        // The vertex count on GeometryPartition is used for allocation sizing, so an upper bound
        // (range) is acceptable and avoids per-partition allocation.
        int minVertex = Integer.MAX_VALUE;
        int maxVertex = Integer.MIN_VALUE;
        for (long prim : primitives) {
            for (int v = 0; v < ipp; v++) {
                long idxOffset = (prim * ipp + v) * 4L;
                int vertexIndex = indexData.get(JAVA_INT_UNALIGNED, idxOffset);
                minVertex = Math.min(minVertex, vertexIndex);
                maxVertex = Math.max(maxVertex, vertexIndex);
            }
        }
        return (minVertex <= maxVertex) ? (maxVertex - minVertex + 1) : 0;
    }
}
