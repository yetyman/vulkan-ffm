package io.github.yetyman.helpers.math.spatial;

import io.github.yetyman.helpers.math.geometry.AABB;

/**
 * Visitor for traversing internal nodes of a spatial structure.
 * Used for visualization, debugging, and serialization.
 */
@FunctionalInterface
public interface NodeVisitor {
    /**
     * Called for each node in the structure.
     *
     * @param bounds the bounding box of this node
     * @param depth the depth of this node (root = 0)
     * @param isLeaf true if this node contains items directly
     * @param itemCount number of items in this node (leaf) or children (internal)
     */
    void visit(AABB bounds, int depth, boolean isLeaf, int itemCount);
}
