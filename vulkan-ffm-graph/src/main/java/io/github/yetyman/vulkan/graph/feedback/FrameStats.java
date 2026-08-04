package io.github.yetyman.vulkan.graph.feedback;

import io.github.yetyman.vulkan.graph.nodes.NodeStats;

import java.util.Map;

/**
 * Aggregate statistics for a completed frame. Contains per-node GPU timestamps,
 * total frame time, and memory usage.
 *
 * The nodeStats map is a double-buffered snapshot owned by RenderGraphStats.
 * It remains valid for two frames (until the snapshot buffer is reused).
 * Consumers should read values immediately in onStats() callbacks rather than
 * holding references across frames.
 */
public class FrameStats {

    private final long frameGeneration;
    private final long totalGpuNanos;
    private final long totalCpuNanos;
    private final Map<String, NodeStats> nodeStats;

    public FrameStats(long frameGeneration, long totalGpuNanos, long totalCpuNanos, Map<String, NodeStats> nodeStats) {
        this.frameGeneration = frameGeneration;
        this.totalGpuNanos = totalGpuNanos;
        this.totalCpuNanos = totalCpuNanos;
        this.nodeStats = nodeStats;
    }

    /** @return monotonic frame counter */
    public long frameGeneration() { return frameGeneration; }

    /** @return total GPU time for the frame in nanoseconds */
    public long totalGpuNanos() { return totalGpuNanos; }

    /** @return total CPU recording time in nanoseconds */
    public long totalCpuNanos() { return totalCpuNanos; }

    /** @return per-node stats keyed by node name */
    public Map<String, NodeStats> nodeStats() { return nodeStats; }

    /** @return stats for a specific node, or null if not found */
    public NodeStats forNode(String name) { return nodeStats.get(name); }

    /** @return total GPU time in milliseconds */
    public double totalGpuMs() { return totalGpuNanos / 1_000_000.0; }
}
