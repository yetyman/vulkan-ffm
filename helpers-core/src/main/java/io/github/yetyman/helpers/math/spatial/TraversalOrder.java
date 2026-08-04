package io.github.yetyman.helpers.math.spatial;

/**
 * Defines the order in which nodes are visited during iteration/serialization.
 */
public enum TraversalOrder {
    /** Depth-first: left child immediately after parent. */
    DFS,
    /** Breadth-first: level by level. */
    BFS,
    /** Morton/Z-order: spatially coherent linear ordering. */
    MORTON
}
