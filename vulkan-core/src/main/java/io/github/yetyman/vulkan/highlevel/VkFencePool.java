package io.github.yetyman.vulkan.highlevel;

import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkResult;
import io.github.yetyman.vulkan.Vulkan;
import io.github.yetyman.vulkan.enums.VkStructureType;
import io.github.yetyman.vulkan.generated.VkFenceCreateInfo;
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
 * MemorySegment fence = fencePool.acquire();
 * // ... use fence for synchronization ...
 * fencePool.release(fence);
 * ```
 */
public class VkFencePool implements AutoCloseable {
    private final VkDevice device;
    private final Arena arena;
    private final Queue<MemorySegment> availableFences = new ArrayDeque<>();
    private final Set<MemorySegment> allFences = new HashSet<>();
    private final int maxPoolSize;
    
    private VkFencePool(VkDevice device, Arena arena, int maxPoolSize) {
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
    public synchronized MemorySegment acquire() {
        if (!availableFences.isEmpty()) {
            MemorySegment fence = availableFences.poll();
            resetFence(fence);
            return fence;
        }
        
        MemorySegment fenceInfo = VkFenceCreateInfo.allocate(arena);
        VkFenceCreateInfo.sType(fenceInfo, VkStructureType.VK_STRUCTURE_TYPE_FENCE_CREATE_INFO.value());
        
        MemorySegment fencePtr = arena.allocate(ValueLayout.ADDRESS);
        Vulkan.createFence(device.handle(), fenceInfo, fencePtr).check();
        MemorySegment fence = fencePtr.get(ValueLayout.ADDRESS, 0);
        
        allFences.add(fence);
        return fence;
    }
    
    /**
     * Returns a fence to the pool for reuse.
     */
    public synchronized void release(MemorySegment fence) {
        if (allFences.contains(fence) && availableFences.size() < maxPoolSize) {
            availableFences.offer(fence);
        }
    }
    
    /**
     * Waits for a fence with timeout.
     */
    public VkResult waitForFence(MemorySegment fence, long timeoutNs) {
        MemorySegment fenceArray = arena.allocate(ValueLayout.ADDRESS);
        fenceArray.set(ValueLayout.ADDRESS, 0, fence);
        return Vulkan.waitForFences(device.handle(), 1, fenceArray, 1, timeoutNs);
    }
    
    private void resetFence(MemorySegment fence) {
        MemorySegment fenceArray = arena.allocate(ValueLayout.ADDRESS);
        fenceArray.set(ValueLayout.ADDRESS, 0, fence);
        Vulkan.resetFences(device.handle(), 1, fenceArray).check();
    }
    
    /**
     * Gets current pool statistics.
     */
    public synchronized PoolStats getStats() {
        return new PoolStats(availableFences.size(), allFences.size(), maxPoolSize);
    }
    
    @Override
    public synchronized void close() {
        for (MemorySegment fence : allFences) {
            Vulkan.destroyFence(device.handle(), fence);
        }
        allFences.clear();
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
        
        public VkFencePool build(Arena arena) {
            if (device == null) throw new IllegalStateException("device not set");
            return new VkFencePool(device, arena, maxPoolSize);
        }
    }
}