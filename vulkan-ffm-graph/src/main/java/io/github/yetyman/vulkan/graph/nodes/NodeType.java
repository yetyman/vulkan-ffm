package io.github.yetyman.vulkan.graph.nodes;

/**
 * The type of work a render node performs.
 */
public enum NodeType {
    GRAPHICS,
    COMPUTE,
    TRANSFER,
    CPU_WORK,
    PRESENT
}
