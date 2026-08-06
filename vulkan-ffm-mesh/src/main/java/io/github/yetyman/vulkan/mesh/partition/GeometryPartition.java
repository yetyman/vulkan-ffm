package io.github.yetyman.vulkan.mesh.partition;

import io.github.yetyman.helpers.math.geometry.AABB;
import io.github.yetyman.vulkan.mesh.PrimitiveTopology;

/**
 * A named contiguous range of a geometry. This one type covers submeshes, meshlets, terrain tiles,
 * Nanite-style clusters, and chunk boundaries. They differ only in their metadata and their count.
 *
 * <p>{@link #tag()} and {@link #sortKey()} are deliberately opaque: this module never interprets
 * them. Consumers use {@code tag} to route partitions to pipelines, materials, or any other
 * app-defined classification. {@code sortKey} is used for draw ordering (depth, pipeline, etc.).
 * Two fields rather than one because routing identity and sort order are genuinely independent.
 *
 * @param name           diagnostic name (may be empty)
 * @param firstIndex     first index into the index stream (or first vertex when non-indexed)
 * @param primitiveCount number of primitives in this partition
 * @param vertexCount    number of vertices in this partition
 * @param topology       primitive topology
 * @param bounds         axis-aligned bounds of this partition
 * @param tag            opaque routing identity; uninterpreted by this module
 * @param sortKey        opaque ordering key; uninterpreted by this module
 */
public record GeometryPartition(
        String name,
        long firstIndex,
        long primitiveCount,
        long vertexCount,
        PrimitiveTopology topology,
        AABB bounds,
        long tag,
        long sortKey
) {
    public GeometryPartition {
        if (topology == null) throw new IllegalArgumentException("topology required");
        if (bounds == null) throw new IllegalArgumentException("bounds required");
        if (name == null) name = "";
    }

    /**
     * @return number of indices this partition consumes, based on its topology and primitive count
     */
    public long indexCount() {
        int ipp = topology.indicesPerPrimitive();
        return ipp > 0 ? primitiveCount * ipp : primitiveCount;
    }

    /**
     * @return true if this partition uses indexed drawing
     */
    public boolean isIndexed() {
        return topology.indicesPerPrimitive() > 0;
    }
}
