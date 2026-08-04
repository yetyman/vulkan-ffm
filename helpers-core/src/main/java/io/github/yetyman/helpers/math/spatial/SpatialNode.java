package io.github.yetyman.helpers.math.spatial;

import io.github.yetyman.helpers.math.geometry.AABB;

/**
 * Handle representing a node in a spatial structure.
 * Provides stable addressing for external sync systems.
 */
public interface SpatialNode {

    /** Stable index for this node. May be invalidated on full rebuild. */
    int index();

    /** The bounding box of this node (encloses all children/items). */
    AABB bounds();

    /** True if this is a leaf node containing items rather than child nodes. */
    boolean isLeaf();

    /** Number of children (internal node) or items (leaf node). */
    int childCount();

    /** Depth of this node in the tree (root = 0). */
    int depth();
}
