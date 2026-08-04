package io.github.yetyman.vulkan.graph.nodes;

/**
 * Per-node timing statistics from a completed frame. Provided to nodes via onStats()
 * so they can adapt their behavior (e.g. switch to half-res if over budget).
 */
public record NodeStats(
    /** GPU execution time in nanoseconds */
    long gpuTimeNanos,
    /** CPU recording time in nanoseconds */
    long cpuRecordTimeNanos,
    /** Frame generation this stat came from */
    long frameGeneration
) {
    /** @return GPU time in milliseconds */
    public double gpuMs() { return gpuTimeNanos / 1_000_000.0; }

    /** @return CPU record time in milliseconds */
    public double cpuRecordMs() { return cpuRecordTimeNanos / 1_000_000.0; }
}
