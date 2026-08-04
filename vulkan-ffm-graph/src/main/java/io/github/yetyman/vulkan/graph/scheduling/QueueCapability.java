package io.github.yetyman.vulkan.graph.scheduling;

/**
 * Declares what queue capability a node requires.
 */
public enum QueueCapability {
    /** Requires a graphics queue */
    GRAPHICS,
    /** Requires a compute queue (may be async compute or graphics) */
    COMPUTE,
    /** Requires a transfer queue (any queue supports transfer) */
    TRANSFER,
    /** No specific requirement -- scheduler assigns freely */
    ANY
}
