package io.github.yetyman.vulkan.graph;

import io.github.yetyman.vulkan.graph.feedback.FrameStats;
import io.github.yetyman.vulkan.graph.nodes.NodeStats;

import java.util.HashMap;
import java.util.Map;

/**
 * Collects per-frame timing data and produces FrameStats.
 * Pre-allocates internal maps to avoid per-frame HashMap creation.
 * Uses a double-buffer approach: the snapshot map alternates between two pre-allocated
 * maps so that the previous FrameStats remains valid while the next frame collects data.
 */
public class RenderGraphStats {

    private long frameGeneration;
    private final HashMap<String, Long> cpuRecordTimes;
    private final HashMap<String, Long> gpuTimes;
    private final HashMap<String, NodeStats>[] snapshotBuffers;
    private int currentSnapshot = 0;
    private long totalGpuNanos;
    private long totalCpuNanos;

    public RenderGraphStats() {
        this(32);
    }

    /** @param expectedNodeCount pre-size internal maps to this capacity */
    @SuppressWarnings("unchecked")
    public RenderGraphStats(int expectedNodeCount) {
        this.cpuRecordTimes = HashMap.newHashMap(expectedNodeCount);
        this.gpuTimes = HashMap.newHashMap(expectedNodeCount);
        this.snapshotBuffers = new HashMap[]{
            HashMap.newHashMap(expectedNodeCount),
            HashMap.newHashMap(expectedNodeCount)
        };
    }

    /** Begins collecting for a new frame. Clears previous data without reallocating. */
    public void beginFrame(long frameGeneration) {
        this.frameGeneration = frameGeneration;
        cpuRecordTimes.clear();
        gpuTimes.clear();
        totalGpuNanos = 0;
        totalCpuNanos = 0;
    }

    /** Records CPU recording time for a node (provided by executor) */
    public void recordCpuTime(String nodeName, long nanos) {
        cpuRecordTimes.put(nodeName, nanos);
        totalCpuNanos += nanos;
    }

    /** Records all CPU times from executor output */
    public void recordCpuTimes(Map<String, Long> times) {
        for (var entry : times.entrySet()) {
            recordCpuTime(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Records GPU timestamp delta for a node (from query pool readback).
     * The caller is responsible for converting timestamp ticks to nanoseconds
     * using the device's timestampPeriod.
     */
    public void recordGpuTime(String nodeName, long nanos) {
        gpuTimes.put(nodeName, nanos);
        totalGpuNanos += nanos;
    }

    /**
     * Builds the final FrameStats for this frame. Uses a pre-allocated double-buffered
     * snapshot map to avoid per-frame allocation. The returned FrameStats is valid until
     * the second subsequent call to build() (two frames later).
     */
    public FrameStats build() {
        // Swap to the other snapshot buffer
        currentSnapshot = 1 - currentSnapshot;
        HashMap<String, NodeStats> snapshot = snapshotBuffers[currentSnapshot];
        snapshot.clear();

        for (String name : cpuRecordTimes.keySet()) {
            long cpu = cpuRecordTimes.getOrDefault(name, 0L);
            long gpu = gpuTimes.getOrDefault(name, 0L);
            snapshot.put(name, new NodeStats(gpu, cpu, frameGeneration));
        }
        for (String name : gpuTimes.keySet()) {
            if (!snapshot.containsKey(name)) {
                snapshot.put(name, new NodeStats(gpuTimes.get(name), 0, frameGeneration));
            }
        }
        // FrameStats wraps the snapshot directly (no copy). Valid until 2 frames later.
        return new FrameStats(frameGeneration, totalGpuNanos, totalCpuNanos, snapshot);
    }
}
