package io.github.yetyman.vulkan.graph;

import io.github.yetyman.vulkan.Vulkan;
import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages a VkQueryPool for GPU timestamp queries. Allocates pairs of queries
 * (begin/end) for each node and provides readback of elapsed time in nanoseconds.
 */
public class TimestampQueryPool implements AutoCloseable {

    private final VkDevice device;
    private final MemorySegment handle;
    private final int maxNodes;
    private final float timestampPeriod; // nanoseconds per tick
    private final Map<String, Integer> nodeQueryIndices = new HashMap<>();
    private int nextQueryIndex = 0;

    private TimestampQueryPool(VkDevice device, MemorySegment handle, int maxNodes, float timestampPeriod) {
        this.device = device;
        this.handle = handle;
        this.maxNodes = maxNodes;
        this.timestampPeriod = timestampPeriod;
    }

    /**
     * Creates a timestamp query pool.
     *
     * @param device the logical device
     * @param maxNodes maximum number of nodes to time (allocates 2 queries per node)
     * @param timestampPeriod nanoseconds per timestamp tick (from VkPhysicalDeviceLimits)
     * @param arena arena for allocation
     */
    public static TimestampQueryPool create(VkDevice device, int maxNodes, float timestampPeriod, Arena arena) {
        MemorySegment createInfo = VkQueryPoolCreateInfo.allocate(arena);
        VkQueryPoolCreateInfo.sType(createInfo, VkStructureType.VK_STRUCTURE_TYPE_QUERY_POOL_CREATE_INFO.value());
        VkQueryPoolCreateInfo.pNext(createInfo, MemorySegment.NULL);
        VkQueryPoolCreateInfo.flags(createInfo, 0);
        VkQueryPoolCreateInfo.queryType(createInfo, VkQueryType.VK_QUERY_TYPE_TIMESTAMP.value());
        VkQueryPoolCreateInfo.queryCount(createInfo, maxNodes * 2);
        VkQueryPoolCreateInfo.pipelineStatistics(createInfo, 0);

        MemorySegment poolPtr = arena.allocate(ValueLayout.ADDRESS);
        Vulkan.createQueryPool(device.handle(), createInfo, poolPtr).check();
        MemorySegment pool = poolPtr.get(ValueLayout.ADDRESS, 0);

        return new TimestampQueryPool(device, pool, maxNodes, timestampPeriod);
    }

    /** @return the VkQueryPool handle */
    public MemorySegment handle() { return handle; }

    /**
     * Resets all queries and clears node assignments. Call at the start of each frame.
     */
    public void reset(MemorySegment commandBuffer) {
        Vulkan.cmdResetQueryPool(commandBuffer, handle, 0, maxNodes * 2);
        nodeQueryIndices.clear();
        nextQueryIndex = 0;
    }

    /**
     * Resets queries from the host (Vulkan 1.2+). Call before recording if not using cmd reset.
     */
    public void resetFromHost() {
        Vulkan.resetQueryPool(device.handle(), handle, 0, maxNodes * 2);
        nodeQueryIndices.clear();
        nextQueryIndex = 0;
    }

    /**
     * Writes a begin timestamp for the named node.
     */
    public void writeBeginTimestamp(MemorySegment commandBuffer, String nodeName, int pipelineStage) {
        if (nextQueryIndex >= maxNodes) return; // silently skip if pool exhausted
        int queryIdx = nextQueryIndex++;
        nodeQueryIndices.put(nodeName, queryIdx);
        Vulkan.cmdWriteTimestamp(commandBuffer, pipelineStage, handle, queryIdx * 2);
    }

    /**
     * Writes an end timestamp for the named node.
     */
    public void writeEndTimestamp(MemorySegment commandBuffer, String nodeName, int pipelineStage) {
        Integer queryIdx = nodeQueryIndices.get(nodeName);
        if (queryIdx == null) return;
        Vulkan.cmdWriteTimestamp(commandBuffer, pipelineStage, handle, queryIdx * 2 + 1);
    }

    /**
     * Reads back all timestamp results and returns per-node GPU time in nanoseconds.
     * Call after the frame's fence has signaled.
     *
     * @param arena arena for readback buffer allocation
     * @return map of node name to GPU elapsed nanoseconds
     */
    public Map<String, Long> readback(Arena arena) {
        if (nodeQueryIndices.isEmpty()) return Map.of();

        int totalQueries = nextQueryIndex * 2;
        long bufferSize = (long) totalQueries * Long.BYTES;
        MemorySegment results = arena.allocate(ValueLayout.JAVA_LONG, totalQueries);

        int flags = 0x00000001 | 0x00000002; // VK_QUERY_RESULT_64_BIT | VK_QUERY_RESULT_WAIT_BIT
        Vulkan.getQueryPoolResults(device.handle(), handle, 0, totalQueries,
            bufferSize, results, Long.BYTES, flags);

        Map<String, Long> gpuTimes = new HashMap<>();
        for (var entry : nodeQueryIndices.entrySet()) {
            int idx = entry.getValue();
            long begin = results.getAtIndex(ValueLayout.JAVA_LONG, idx * 2);
            long end = results.getAtIndex(ValueLayout.JAVA_LONG, idx * 2 + 1);
            long ticks = end - begin;
            long nanos = (long) (ticks * timestampPeriod);
            gpuTimes.put(entry.getKey(), Math.max(0, nanos));
        }
        return gpuTimes;
    }

    @Override
    public void close() {
        Vulkan.destroyQueryPool(device.handle(), handle);
    }
}
