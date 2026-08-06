package io.github.yetyman.vulkan.mesh.residency;

/**
 * How an allocator handles index values relative to a shared vertex pool.
 */
public enum IndexBaseMode {
    /**
     * Indices are rewritten at upload time to be absolute within the pool.
     * Costs a transcode pass over indices but allows merging draws that would otherwise
     * need different vertexOffset values.
     */
    REWRITE_ABSOLUTE,

    /**
     * Indices stay relative to zero; draws carry a vertexOffset that compensates.
     * Free at upload time, and what {@code vkCmdDrawIndexed}'s vertexOffset was designed for.
     */
    RELATIVE_WITH_DRAW_OFFSET
}
