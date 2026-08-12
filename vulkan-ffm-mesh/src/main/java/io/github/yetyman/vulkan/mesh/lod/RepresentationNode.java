package io.github.yetyman.vulkan.mesh.lod;

import io.github.yetyman.helpers.math.geometry.AABB;

/**
 * One node in a representation structure: a set of partitions that together form a valid
 * renderable at a certain level of detail.
 *
 * <p>This is pure structural data. It says nothing about selection, rendering, or residency.
 * A node knows which partitions it covers, what geometric error it introduces relative to the
 * original, and how much geometry it contains. All other metadata (cone axes, cluster DAG
 * specifics, progressive mesh refinement records) belongs in {@link io.github.yetyman.vulkan.mesh.partition.MetadataChannel}
 * attached to the partitions, not here.
 *
 * <p>The relationship between nodes (parent/child, chain ordering, DAG edges) is expressed by
 * the containing {@link RepresentationStructure}, not by references on this record. This keeps
 * nodes as plain value data with no graph pointers and makes them safe to copy, reorder, and
 * upload to the GPU as a dense array.
 *
 * @param partitionIndices indices into the owning geometry's {@link io.github.yetyman.vulkan.mesh.partition.PartitionSet};
 *                         together these partitions cover the geometry at this detail level
 * @param errorBound       maximum geometric error this representation introduces relative to the
 *                         full-detail original, in world-space units. 0.0 means lossless (full
 *                         detail). Used by screen-error selectors to project to pixels.
 *                         Negative means "error not tracked" (e.g. parametric representations).
 * @param triangleCount    total triangle count across all partitions in this node; used by
 *                         budget-aware selectors. 0 means "not applicable" (e.g. parametric).
 * @param bounds           world-space AABB enclosing all partitions in this node
 * @param tag              opaque routing identity (same semantics as GeometryPartition.tag)
 */
public record RepresentationNode(
        int[] partitionIndices,
        float errorBound,
        long triangleCount,
        AABB bounds,
        long tag
) {
    public RepresentationNode {
        if (partitionIndices == null) throw new IllegalArgumentException("partitionIndices required");
        if (bounds == null) throw new IllegalArgumentException("bounds required");
    }

    /**
     * @return number of partitions this node covers
     */
    public int partitionCount() {
        return partitionIndices.length;
    }

    /**
     * @return true if this node has a meaningful error bound (non-negative)
     */
    public boolean hasErrorBound() {
        return errorBound >= 0.0f;
    }
}
