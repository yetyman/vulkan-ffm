package io.github.yetyman.vulkan;

import java.lang.foreign.*;

/**
 * Wrapper for Vulkan descriptor set (VkDescriptorSet).
 * Descriptor sets contain bindings to resources like buffers, images, and samplers.
 */
public class VkDescriptorSet {
    private final MemorySegment handle;
    
    public VkDescriptorSet(MemorySegment handle) {
        this.handle = handle;
    }
    
    /** @return the VkDescriptorSet handle */
    public MemorySegment handle() { return handle; }
}