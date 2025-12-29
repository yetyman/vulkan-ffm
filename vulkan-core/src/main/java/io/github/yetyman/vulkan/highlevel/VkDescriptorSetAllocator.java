package io.github.yetyman.vulkan.highlevel;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Efficient descriptor set allocator with automatic recycling and pool management.
 * 
 * Example usage:
 * <pre>{@code
 * // Create allocator
 * VkDescriptorSetAllocator allocator = VkDescriptorSetAllocator.builder()
 *     .device(device)
 *     .maxSetsPerPool(128)
 *     .build(arena);
 * 
 * // Allocate descriptor sets
 * VkDescriptorSet descriptorSet1 = allocator.allocate(materialLayout);
 * VkDescriptorSet descriptorSet2 = allocator.allocate(lightingLayout);
 * 
 * // Use descriptor sets...
 * updateDescriptorSet(descriptorSet1, texture, uniformBuffer);
 * 
 * // Free when done (returns to pool for reuse)
 * allocator.free(descriptorSet1);
 * allocator.free(descriptorSet2);
 * }</pre>
 */
public class VkDescriptorSetAllocator implements AutoCloseable {
    private final MemorySegment device;
    private final Arena arena;
    private final Map<LayoutKey, DescriptorPool> pools = new ConcurrentHashMap<>();
    private final int maxSetsPerPool;
    
    private VkDescriptorSetAllocator(MemorySegment device, Arena arena, int maxSetsPerPool) {
        this.device = device;
        this.arena = arena;
        this.maxSetsPerPool = maxSetsPerPool;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Allocates a descriptor set for the given layout.
     */
    public VkDescriptorSet allocate(VkDescriptorSetLayout descriptorSetLayout) {
        LayoutKey key = new LayoutKey(descriptorSetLayout.handle());
        DescriptorPool pool = pools.computeIfAbsent(key, k -> createPool(descriptorSetLayout));
        return pool.allocate();
    }
    
    /**
     * Frees a descriptor set back to the pool.
     */
    public void free(VkDescriptorSet descriptorSet) {
        // Find pool by searching all pools (could be optimized with reverse mapping)
        for (DescriptorPool pool : pools.values()) {
            if (pool.free(descriptorSet)) {
                return;
            }
        }
    }
    
    private DescriptorPool createPool(VkDescriptorSetLayout layout) {
        VkDescriptorPool vkPool = VkDescriptorPool.builder()
            .device(device)
            .maxSets(maxSetsPerPool)
            .uniformBuffers(maxSetsPerPool * 2)
            .combinedImageSamplers(maxSetsPerPool * 4)
            .storageBuffers(maxSetsPerPool)
            .storageImages(maxSetsPerPool)
            .freeDescriptorSet()
            .build(arena);
        
        return new DescriptorPool(vkPool, layout);
    }
    
    @Override
    public void close() {
        for (DescriptorPool pool : pools.values()) {
            pool.close();
        }
        pools.clear();
    }
    
    private class DescriptorPool implements AutoCloseable {
        private final VkDescriptorPool vkPool;
        private final VkDescriptorSetLayout layout;
        private final Queue<VkDescriptorSet> freeSets = new ArrayDeque<>();
        private final Set<MemorySegment> allocatedHandles = new HashSet<>();
        private int allocatedCount = 0;
        
        DescriptorPool(VkDescriptorPool vkPool, VkDescriptorSetLayout layout) {
            this.vkPool = vkPool;
            this.layout = layout;
        }
        
        VkDescriptorSet allocate() {
            VkDescriptorSet set = freeSets.poll();
            if (set != null) {
                return set;
            }
            
            if (allocatedCount >= maxSetsPerPool) {
                throw new RuntimeException("Descriptor pool exhausted");
            }
            
            VkDescriptorSet newSet = vkPool.allocateDescriptorSet(layout);
            allocatedHandles.add(newSet.handle());
            allocatedCount++;
            return newSet;
        }
        
        boolean free(VkDescriptorSet descriptorSet) {
            if (allocatedHandles.contains(descriptorSet.handle())) {
                freeSets.offer(descriptorSet);
                return true;
            }
            return false;
        }
        
        @Override
        public void close() {
            vkPool.close();
        }
    }
    
    private record LayoutKey(MemorySegment layout) {}
    
    public static class Builder {
        private MemorySegment device;
        private int maxSetsPerPool = 64;
        
        public Builder device(MemorySegment device) {
            this.device = device;
            return this;
        }
        
        public Builder maxSetsPerPool(int maxSets) {
            this.maxSetsPerPool = maxSets;
            return this;
        }
        
        public VkDescriptorSetAllocator build(Arena arena) {
            if (device == null) throw new IllegalStateException("device not set");
            return new VkDescriptorSetAllocator(device, arena, maxSetsPerPool);
        }
    }
}