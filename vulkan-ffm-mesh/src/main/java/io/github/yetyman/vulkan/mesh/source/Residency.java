package io.github.yetyman.vulkan.mesh.source;

/**
 * Where a stream's data currently lives. This is a state on the stream, not a type distinction:
 * a stream can transition between states as data is uploaded, evicted, or generated.
 *
 * <p>{@link #ABSENT} is a legitimate steady state, not an error: a procedural source that has not
 * been asked to generate yet, or a streamed partition that has been evicted.
 */
public enum Residency {
    /** Data does not exist yet. Normal for procedural sources before generation, or after eviction. */
    ABSENT,
    /** An upload or generation is in progress but not yet usable. */
    PENDING,
    /** Data is available on the host (CPU-readable). */
    HOST,
    /** Data is available on the device (GPU-readable). */
    DEVICE,
    /** Data is available on both host and device. */
    HOST_AND_DEVICE,
    /** Data is being evicted; still device-resident but will transition to ABSENT. */
    EVICTING
}
