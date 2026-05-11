package io.github.yetyman.vulkan.graph;

import io.github.yetyman.vulkan.graph.feedback.FrameStats;
import io.github.yetyman.vulkan.graph.nodes.NodeStats;

import java.util.HashMap;
import java.util.Map;

/**
 * Collects per-frame timing data and produces FrameStats.
 * Pre-allocates internal maps to avoid per-frame HashMap creation.
 * The build() output is a new FrameStats each frame (immutable snapshot for consumers),
 * but the internal collection maps are reused.
 */
public class RenderGraphStats {

    private long frameGeneration;
    private final HashMap<String, Long> cpuRecordTimes;
    private final HashMap<String, Long> gpuTimes;
    private final HashMap<String, NodeStats> nodeStatsBuffer;
    private long totalGpuNanos;
    private long totalCpuNanos;

    public RenderGraphStats() {
        this(32);
    }

    /** @param expectedNodeCount pre-size internal maps to this capacity */
    public RenderGraphStats(int expectedNodeCount) {
        this.cpuRecordTimes = HashMap.newHashMap(expectedNodeCount);
        this.gpuTimes = HashMap.newHashMap(expectedNodeCount);
        this.nodeStatsBuffer = HashMap.newHashMap(expectedNodeCount);
    }

    /** Begins collecting for a new frame. Clears previous data without reallocating. */
    public void beginFrame(long frameGeneration) {
        this.frameGeneration = frameGeneration;
        cpuRecordTimes.clear();
        gpuTimes.clear();
        nodeStatsBuffer.clear();
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

    /** Builds the final FrameStats for this frame. Allocates a new map for the immutable snapshot. */
    public FrameStats build() {
        nodeStatsBuffer.clear();
        for (String name : cpuRecordTimes.keySet()) {
            long cpu = cpuRecordTimes.getOrDefault(name, 0L);
            long gpu = gpuTimes.getOrDefault(name, 0L);
            nodeStatsBuffer.put(name, new NodeStats(gpu, cpu, frameGeneration));
        }
        for (String name : gpuTimes.keySet()) {
            if (!nodeStatsBuffer.containsKey(name)) {
                nodeStatsBuffer.put(name, new NodeStats(gpuTimes.get(name), 0, frameGeneration));
            }
        }
        // Snapshot: FrameStats takes ownership of a copy
        return new FrameStats(frameGeneration, totalGpuNanos, totalCpuNanos, new HashMap<>(nodeStatsBuffer));
    }
}

