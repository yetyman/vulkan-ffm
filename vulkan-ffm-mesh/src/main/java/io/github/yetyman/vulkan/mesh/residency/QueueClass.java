package io.github.yetyman.vulkan.mesh.residency;

/**
 * Preferred queue class for an upload operation. A class, not a concrete queue: the executor
 * resolves it to a real queue based on the device's topology and what else is competing.
 */
public enum QueueClass {
    /** Prefer a dedicated transfer queue for maximum concurrency. */
    TRANSFER,
    /** Prefer a compute queue (e.g. for compute-generated geometry). */
    COMPUTE,
    /** Prefer the graphics queue (e.g. when the consumer is the same submission). */
    GRAPHICS
}
