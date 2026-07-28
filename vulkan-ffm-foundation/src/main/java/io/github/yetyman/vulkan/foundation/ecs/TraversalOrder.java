package io.github.yetyman.vulkan.foundation.ecs;

/**
 * Traversal ordering strategy for ComponentTreeTraversalView.
 *
 * Determines the order in which nodes appear in the per-component-type traversal list.
 */
public enum TraversalOrder {
    /** Depth-first pre-order: parent before children, left before right. */
    DEPTH_FIRST_PRE_ORDER,

    /** Depth-first post-order: children before parent. */
    DEPTH_FIRST_POST_ORDER,

    /** Breadth-first: level by level, left to right. */
    BREADTH_FIRST
}
