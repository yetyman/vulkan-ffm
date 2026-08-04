package io.github.yetyman.helpers.math.spatial.bvh;

import io.github.yetyman.helpers.math.geometry.AABB;

import java.util.List;

/**
 * Strategy interface for BVH construction. Implementations define how primitives
 * are partitioned into a binary tree.
 */
public interface BvhBuilder {

    /**
     * Result of BVH construction: the node array and the reordered primitive indices.
     */
    record BuildResult(BvhNode[] nodes, int[] orderedIndices) {}

    /**
     * Builds a flat array of BVH nodes from the given bounds.
     * Returns the node array and the permutation of original indices
     * (leaf primStart/primCount reference positions in orderedIndices).
     *
     * @param bounds list of AABBs, one per item
     * @return build result with nodes and index ordering
     */
    BuildResult build(List<AABB> bounds);
}
