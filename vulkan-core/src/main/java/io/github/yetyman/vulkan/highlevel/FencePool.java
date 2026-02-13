package io.github.yetyman.vulkan.highlevel;

import io.github.yetyman.vulkan.*;
import java.lang.foreign.*;
import java.util.*;

/**
 * Pool for efficient fence reuse to reduce allocation overhead.
 * 
 * Example usage:
 * ```java
 * VkFencePool fencePool = VkFencePool.builder()
 *     .device(device)
 *     .maxPoolSize(16)
 *     .build(arena);
 * 
 * VkFence fence = fencePool.acquire();
 * // ... use fence for synchronization ...
 * fencePool.release(fence);
 * ```
 */
public class FencePool implements AutoCloseable {
    private final VkDevice device;
    private final Arena arena;
    private final Queue<VkFence> availableFences = new ArrayDeque<>();
    private final Set<MemorySegment> allFenceHandles = new HashSet<>();
    private final int maxPoolSize;
    
    private FencePool(VkDevice device, Arena arena, int maxPoolSize) {
        this.device = device;
        this.arena = arena;
        this.maxPoolSize = maxPoolSize;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Acquires a fence from the pool, creating new one if needed.
     */
    public synchronized VkFence acquire() {
        if (!availableFences.isEmpty()) {
            VkFence fence = availableFences.poll();
            resetFence(fence);
            return fence;
        }
        
        VkFence fence = VkFence.builder()
            .device(device)
            .build(arena);
        
        allFenceHandles.add(fence.handle());
        return fence;
    }
    
    /**
     * Returns a fence to the pool for reuse.
     */
    public synchronized void release(VkFence fence) {
        if (allFenceHandles.contains(fence.handle()) && availableFences.size() < maxPoolSize) {
            availableFences.offer(fence);
        }
    }
    
    /**
     * Waits for a fence with timeout.
     */
    public VkResult waitForFence(VkFence fence, long timeoutNs) {
        MemorySegment fenceArray = arena.allocate(ValueLayout.ADDRESS);
        fenceArray.set(ValueLayout.ADDRESS, 0, fence.handle());
        return Vulkan.waitForFences(device.handle(), 1, fenceArray, 1, timeoutNs);
    }
    
    private void resetFence(VkFence fence) {
        MemorySegment fenceArray = arena.allocate(ValueLayout.ADDRESS);
        fenceArray.set(ValueLayout.ADDRESS, 0, fence.handle());
        Vulkan.resetFences(device.handle(), 1, fenceArray).check();
    }
    
    /**
     * Gets current pool statistics.
     */
    public synchronized PoolStats getStats() {
        return new PoolStats(availableFences.size(), allFenceHandles.size(), maxPoolSize);
    }
    
    @Override
    public synchronized void close() {
        // Close all fences (both available and in-use)
        for (VkFence fence : availableFences) {
            fence.close();
        }
        allFenceHandles.clear();
        availableFences.clear();
    }
    
    public static class PoolStats {
        public final int available;
        public final int total;
        public final int maxSize;
        
        PoolStats(int available, int total, int maxSize) {
            this.available = available;
            this.total = total;
            this.maxSize = maxSize;
        }
    }
    
    public static class Builder {
        private VkDevice device;
        private int maxPoolSize = 32;
        
        public Builder device(VkDevice device) {
            this.device = device;
            return this;
        }
        
        public Builder maxPoolSize(int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
            return this;
        }
        
        public FencePool build(Arena arena) {
            if (device == null) throw new IllegalStateException("device not set");
            return new FencePool(device, arena, maxPoolSize);
        }
    }
}