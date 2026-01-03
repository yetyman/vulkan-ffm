package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Vulkan descriptor pool (VkDescriptorPool) with automatic resource management.
 * Descriptor pools manage memory for descriptor sets.
 */
public class VkDescriptorPool implements AutoCloseable {
    private final MemorySegment handle;
    private final VkDevice device;
    
    public VkDescriptorPool(MemorySegment handle, VkDevice device) {
        this.handle = handle;
        this.device = device;
    }
    
    /** @return a new builder for configuring descriptor pool creation */
    public static Builder builder() {
        return new Builder();
    }
    
    /** @return the VkDescriptorPool handle */
    public MemorySegment handle() { return handle; }
    
    /**
     * Allocates a descriptor set from this pool.
     */
    public VkDescriptorSet allocateDescriptorSet(VkDescriptorSetLayout descriptorSetLayout) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment allocInfo = VkDescriptorSetAllocateInfo.allocate(arena);
            VkDescriptorSetAllocateInfo.sType(allocInfo, VkStructureType.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO);
            VkDescriptorSetAllocateInfo.descriptorPool(allocInfo, handle);
            VkDescriptorSetAllocateInfo.descriptorSetCount(allocInfo, 1);
            
            MemorySegment layouts = arena.allocate(ValueLayout.ADDRESS);
            layouts.set(ValueLayout.ADDRESS, 0, descriptorSetLayout.handle());
            VkDescriptorSetAllocateInfo.pSetLayouts(allocInfo, layouts);
            
            MemorySegment descriptorSet = arena.allocate(ValueLayout.ADDRESS);
            Vulkan.allocateDescriptorSets(device.handle(), allocInfo, descriptorSet).check();
            return new VkDescriptorSet(descriptorSet.get(ValueLayout.ADDRESS, 0));
        }
    }
    
    @Override
    public void close() {
        Vulkan.destroyDescriptorPool(device.handle(), handle);
    }
    
    /**
     * Builder for descriptor pool creation.
     */
    public static class Builder {
        private VkDevice device;
        private int maxSets = 1;
        private final List<PoolSize> poolSizes = new ArrayList<>();
        private int flags = 0;
        
        private Builder() {}
        
        /** Sets the logical device */
        public Builder device(VkDevice device) {
            this.device = device;
            return this;
        }
        
        /** Sets maximum number of descriptor sets that can be allocated */
        public Builder maxSets(int maxSets) {
            this.maxSets = maxSets;
            return this;
        }
        
        /** Adds uniform buffer descriptors to the pool */
        public Builder uniformBuffers(int count) {
            return poolSize(VkDescriptorType.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, count);
        }
        
        /** Adds combined image sampler descriptors to the pool */
        public Builder combinedImageSamplers(int count) {
            return poolSize(VkDescriptorType.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, count);
        }
        
        /** Adds storage buffer descriptors to the pool */
        public Builder storageBuffers(int count) {
            return poolSize(VkDescriptorType.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, count);
        }
        
        /** Adds storage image descriptors to the pool */
        public Builder storageImages(int count) {
            return poolSize(VkDescriptorType.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE, count);
        }
        
        /** Adds a custom descriptor type to the pool */
        public Builder poolSize(int descriptorType, int descriptorCount) {
            poolSizes.add(new PoolSize(descriptorType, descriptorCount));
            return this;
        }
        
        /** Enables free descriptor set flag */
        public Builder freeDescriptorSet() {
            this.flags |= VkDescriptorPoolCreateFlagBits.VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT;
            return this;
        }
        
        /** Sets creation flags */
        public Builder flags(int flags) {
            this.flags = flags;
            return this;
        }
        
        /** Creates the descriptor pool */
        public VkDescriptorPool build(Arena arena) {
            if (device == null) throw new IllegalStateException("device not set");
            if (poolSizes.isEmpty()) throw new IllegalStateException("no pool sizes defined");
            
            MemorySegment poolSizesArray = arena.allocate(VkDescriptorPoolSize.layout(), poolSizes.size());
            
            for (int i = 0; i < poolSizes.size(); i++) {
                PoolSize ps = poolSizes.get(i);
                MemorySegment poolSize = poolSizesArray.asSlice(i * VkDescriptorPoolSize.layout().byteSize(), VkDescriptorPoolSize.layout());
                VkDescriptorPoolSize.type(poolSize, ps.type());
                VkDescriptorPoolSize.descriptorCount(poolSize, ps.descriptorCount());
            }
            
            MemorySegment createInfo = VkDescriptorPoolCreateInfo.allocate(arena);
            VkDescriptorPoolCreateInfo.sType(createInfo, VkStructureType.VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO);
            VkDescriptorPoolCreateInfo.pNext(createInfo, MemorySegment.NULL);
            VkDescriptorPoolCreateInfo.flags(createInfo, flags);
            VkDescriptorPoolCreateInfo.maxSets(createInfo, maxSets);
            VkDescriptorPoolCreateInfo.poolSizeCount(createInfo, poolSizes.size());
            VkDescriptorPoolCreateInfo.pPoolSizes(createInfo, poolSizesArray);
            
            MemorySegment poolPtr = arena.allocate(ValueLayout.ADDRESS);
            Vulkan.createDescriptorPool(device.handle(), createInfo, poolPtr).check();
            return new VkDescriptorPool(poolPtr.get(ValueLayout.ADDRESS, 0), device);
        }
        
        private record PoolSize(int type, int descriptorCount) {}
    }
}