package io.github.yetyman.vulkan.graph.scheduling;

import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.VkCommandBufferAlloc;
import io.github.yetyman.vulkan.VkCommandPool;
import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.Vulkan;
import io.github.yetyman.vulkan.enums.VkCommandBufferLevel;
import io.github.yetyman.vulkan.enums.VkCommandPoolCreateFlagBits;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages per-queue-family command pools and command buffer allocation for the render graph.
 * Pools are created lazily per queue family. Command buffers are allocated in batches and
 * recycled each frame via pool reset.
 *
 * Supports both primary command buffers (one per queue submission) and secondary command
 * buffers (for parallel node recording within a bucket).
 *
 * Lifecycle: create once, call {@link #resetAll()} at the start of each frame, allocate
 * command buffers as needed during recording, then submit. The pool reset recycles all
 * command buffers without individual free calls.
 */
public class FrameCommandBufferPool implements AutoCloseable {

    private final VkDevice device;
    private final Arena poolArena;
    private final Map<Integer, VkCommandPool> pools = new HashMap<>();
    private final Map<Integer, List<VkCommandBuffer>> primaryBuffers = new HashMap<>();
    private final Map<Integer, List<VkCommandBuffer>> secondaryBuffers = new HashMap<>();
    private final Map<Integer, Integer> primaryAllocIndex = new HashMap<>();
    private final Map<Integer, Integer> secondaryAllocIndex = new HashMap<>();

    private static final int BATCH_SIZE = 8;

    public FrameCommandBufferPool(VkDevice device, Arena poolArena) {
        this.device = device;
        this.poolArena = poolArena;
    }

    /**
     * Creates a FrameCommandBufferPool with its own shared arena.
     */
    public static FrameCommandBufferPool create(VkDevice device) {
        return new FrameCommandBufferPool(device, Arena.ofShared());
    }

    /**
     * Resets all command pools, recycling all command buffers for reuse.
     * Call at the start of each frame before allocating new command buffers.
     */
    public void resetAll() {
        for (Map.Entry<Integer, VkCommandPool> entry : pools.entrySet()) {
            Vulkan.resetCommandPool(device.handle(), entry.getValue().handle(), 0);
            primaryAllocIndex.put(entry.getKey(), 0);
            secondaryAllocIndex.put(entry.getKey(), 0);
        }
    }

    /**
     * Allocates (or reuses) a primary command buffer for the given queue family.
     */
    public VkCommandBuffer allocatePrimary(int queueFamilyIndex) {
        ensurePool(queueFamilyIndex);
        List<VkCommandBuffer> buffers = primaryBuffers.computeIfAbsent(queueFamilyIndex, k -> new ArrayList<>());
        int idx = primaryAllocIndex.getOrDefault(queueFamilyIndex, 0);

        if (idx < buffers.size()) {
            primaryAllocIndex.put(queueFamilyIndex, idx + 1);
            return buffers.get(idx);
        }

        // Need to allocate more
        VkCommandBuffer[] newBuffers = VkCommandBufferAlloc.builder()
            .device(device)
            .commandPool(pools.get(queueFamilyIndex).handle())
            .primary()
            .count(BATCH_SIZE)
            .allocate(poolArena);

        for (VkCommandBuffer buf : newBuffers) {
            buffers.add(buf);
        }

        primaryAllocIndex.put(queueFamilyIndex, idx + 1);
        return buffers.get(idx);
    }

    /**
     * Allocates (or reuses) a secondary command buffer for the given queue family.
     * Used for parallel node recording within execution buckets.
     */
    public VkCommandBuffer allocateSecondary(int queueFamilyIndex) {
        ensurePool(queueFamilyIndex);
        List<VkCommandBuffer> buffers = secondaryBuffers.computeIfAbsent(queueFamilyIndex, k -> new ArrayList<>());
        int idx = secondaryAllocIndex.getOrDefault(queueFamilyIndex, 0);

        if (idx < buffers.size()) {
            secondaryAllocIndex.put(queueFamilyIndex, idx + 1);
            return buffers.get(idx);
        }

        // Need to allocate more
        VkCommandBuffer[] newBuffers = VkCommandBufferAlloc.builder()
            .device(device)
            .commandPool(pools.get(queueFamilyIndex).handle())
            .secondary()
            .count(BATCH_SIZE)
            .allocate(poolArena);

        for (VkCommandBuffer buf : newBuffers) {
            buffers.add(buf);
        }

        secondaryAllocIndex.put(queueFamilyIndex, idx + 1);
        return buffers.get(idx);
    }

    /**
     * Allocates multiple secondary command buffers at once for parallel recording.
     */
    public VkCommandBuffer[] allocateSecondaries(int queueFamilyIndex, int count) {
        VkCommandBuffer[] result = new VkCommandBuffer[count];
        for (int i = 0; i < count; i++) {
            result[i] = allocateSecondary(queueFamilyIndex);
        }
        return result;
    }

    /** @return the command pool for a given queue family, or null if not yet created */
    public VkCommandPool pool(int queueFamilyIndex) {
        return pools.get(queueFamilyIndex);
    }

    private void ensurePool(int queueFamilyIndex) {
        if (pools.containsKey(queueFamilyIndex)) return;
        VkCommandPool pool = VkCommandPool.builder()
            .device(device)
            .queueFamilyIndex(queueFamilyIndex)
            .resetCommandBufferBit()
            .transientBit()
            .build(poolArena);
        pools.put(queueFamilyIndex, pool);
    }

    @Override
    public void close() {
        for (VkCommandPool pool : pools.values()) {
            pool.close();
        }
        pools.clear();
        primaryBuffers.clear();
        secondaryBuffers.clear();
        poolArena.close();
    }
}
