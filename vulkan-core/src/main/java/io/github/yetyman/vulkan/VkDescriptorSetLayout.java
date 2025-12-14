package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Vulkan descriptor set layout (VkDescriptorSetLayout) with automatic resource management.
 * Descriptor set layouts define the interface between shader stages and descriptor sets.
 */
public class VkDescriptorSetLayout implements AutoCloseable {
    private final MemorySegment handle;
    private final MemorySegment device;
    
    private VkDescriptorSetLayout(MemorySegment handle, MemorySegment device) {
        this.handle = handle;
        this.device = device;
    }
    
    /** @return a new builder for configuring descriptor set layout creation */
    public static Builder builder() {
        return new Builder();
    }
    
    /** @return the VkDescriptorSetLayout handle */
    public MemorySegment handle() { return handle; }
    
    @Override
    public void close() {
        VulkanExtensions.destroyDescriptorSetLayout(device, handle);
    }
    
    /**
     * Builder for descriptor set layout creation.
     */
    public static class Builder {
        private MemorySegment device;
        private final List<BindingConfig> bindings = new ArrayList<>();
        private int flags = 0;
        
        private Builder() {}
        
        /** Sets the logical device */
        public Builder device(MemorySegment device) {
            this.device = device;
            return this;
        }
        
        /** Adds a uniform buffer binding */
        public Builder uniformBuffer(int binding, int stageFlags) {
            return binding(binding, VkDescriptorType.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 1, stageFlags);
        }
        
        /** Adds a combined image sampler binding */
        public Builder combinedImageSampler(int binding, int stageFlags) {
            return binding(binding, VkDescriptorType.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 1, stageFlags);
        }
        
        /** Adds a storage buffer binding */
        public Builder storageBuffer(int binding, int stageFlags) {
            return binding(binding, VkDescriptorType.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, 1, stageFlags);
        }
        
        /** Adds a storage image binding */
        public Builder storageImage(int binding, int stageFlags) {
            return binding(binding, VkDescriptorType.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE, 1, stageFlags);
        }
        
        /** Adds a custom descriptor binding */
        public Builder binding(int binding, int descriptorType, int descriptorCount, int stageFlags) {
            bindings.add(new BindingConfig(binding, descriptorType, descriptorCount, stageFlags, 0));
            return this;
        }
        
        /** Sets creation flags */
        public Builder flags(int flags) {
            this.flags = flags;
            return this;
        }
        
        /** Creates the descriptor set layout */
        public VkDescriptorSetLayout build(Arena arena) {
            if (device == null) throw new IllegalStateException("device not set");
            if (bindings.isEmpty()) throw new IllegalStateException("no bindings defined");
            
            MemorySegment bindingsArray = arena.allocate(VkDescriptorSetLayoutBinding.layout(), bindings.size());
            
            for (int i = 0; i < bindings.size(); i++) {
                BindingConfig cfg = bindings.get(i);
                MemorySegment binding = bindingsArray.asSlice(i * VkDescriptorSetLayoutBinding.layout().byteSize(), VkDescriptorSetLayoutBinding.layout());
                VkDescriptorSetLayoutBinding.binding(binding, cfg.binding());
                VkDescriptorSetLayoutBinding.descriptorType(binding, cfg.descriptorType());
                VkDescriptorSetLayoutBinding.descriptorCount(binding, cfg.descriptorCount());
                VkDescriptorSetLayoutBinding.stageFlags(binding, cfg.stageFlags());
                VkDescriptorSetLayoutBinding.pImmutableSamplers(binding, MemorySegment.NULL);
            }
            
            MemorySegment createInfo = VkDescriptorSetLayoutCreateInfo.allocate(arena);
            VkDescriptorSetLayoutCreateInfo.sType(createInfo, VkStructureType.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO);
            VkDescriptorSetLayoutCreateInfo.pNext(createInfo, MemorySegment.NULL);
            VkDescriptorSetLayoutCreateInfo.flags(createInfo, flags);
            VkDescriptorSetLayoutCreateInfo.bindingCount(createInfo, bindings.size());
            VkDescriptorSetLayoutCreateInfo.pBindings(createInfo, bindingsArray);
            
            MemorySegment layoutPtr = arena.allocate(ValueLayout.ADDRESS);
            VulkanExtensions.createDescriptorSetLayout(device, createInfo, layoutPtr).check();
            return new VkDescriptorSetLayout(layoutPtr.get(ValueLayout.ADDRESS, 0), device);
        }
        
        private record BindingConfig(int binding, int descriptorType, int descriptorCount, int stageFlags, int flags) {}
    }
}