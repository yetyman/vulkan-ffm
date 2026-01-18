package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;

/**
 * Wrapper for Vulkan descriptor set (VkDescriptorSet).
 * Descriptor sets contain bindings to resources like buffers, images, and samplers.
 */
public class VkDescriptorSet {
    private final MemorySegment handle;
    private final VkDevice device;
    
    public VkDescriptorSet(MemorySegment handle, VkDevice device) {
        this.handle = handle;
        this.device = device;
    }
    
    /** @return the VkDescriptorSet handle */
    public MemorySegment handle() { return handle; }
    
    /**
     * Updates this descriptor set to bind a uniform buffer
     */
    public void updateBuffer(int binding, int descriptorType, MemorySegment buffer, long offset, long range, Arena arena) {
        MemorySegment bufferInfo = VkDescriptorBufferInfo.allocate(arena);
        VkDescriptorBufferInfo.buffer(bufferInfo, buffer);
        VkDescriptorBufferInfo.offset(bufferInfo, offset);
        VkDescriptorBufferInfo.range(bufferInfo, range);
        
        MemorySegment writeDescriptorSet = VkWriteDescriptorSet.allocate(arena);
        VkWriteDescriptorSet.sType(writeDescriptorSet, VkStructureType.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET.value());
        VkWriteDescriptorSet.pNext(writeDescriptorSet, MemorySegment.NULL);
        VkWriteDescriptorSet.dstSet(writeDescriptorSet, handle);
        VkWriteDescriptorSet.dstBinding(writeDescriptorSet, binding);
        VkWriteDescriptorSet.dstArrayElement(writeDescriptorSet, 0);
        VkWriteDescriptorSet.descriptorCount(writeDescriptorSet, 1);
        VkWriteDescriptorSet.descriptorType(writeDescriptorSet, descriptorType);
        VkWriteDescriptorSet.pBufferInfo(writeDescriptorSet, bufferInfo);
        VkWriteDescriptorSet.pImageInfo(writeDescriptorSet, MemorySegment.NULL);
        VkWriteDescriptorSet.pTexelBufferView(writeDescriptorSet, MemorySegment.NULL);
        
        Vulkan.updateDescriptorSets(device.handle(), 1, writeDescriptorSet, 0, MemorySegment.NULL);
    }
    
    /**
     * Updates this descriptor set to bind an image sampler
     */
    public void updateImageSampler(int binding, MemorySegment sampler, MemorySegment imageView, int imageLayout, Arena arena) {
        MemorySegment imageInfo = VkDescriptorImageInfo.allocate(arena);
        VkDescriptorImageInfo.sampler(imageInfo, sampler);
        VkDescriptorImageInfo.imageView(imageInfo, imageView);
        VkDescriptorImageInfo.imageLayout(imageInfo, imageLayout);
        
        MemorySegment writeDescriptorSet = VkWriteDescriptorSet.allocate(arena);
        VkWriteDescriptorSet.sType(writeDescriptorSet, VkStructureType.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET.value());
        VkWriteDescriptorSet.pNext(writeDescriptorSet, MemorySegment.NULL);
        VkWriteDescriptorSet.dstSet(writeDescriptorSet, handle);
        VkWriteDescriptorSet.dstBinding(writeDescriptorSet, binding);
        VkWriteDescriptorSet.dstArrayElement(writeDescriptorSet, 0);
        VkWriteDescriptorSet.descriptorCount(writeDescriptorSet, 1);
        VkWriteDescriptorSet.descriptorType(writeDescriptorSet, VkDescriptorType.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER.value());
        VkWriteDescriptorSet.pImageInfo(writeDescriptorSet, imageInfo);
        VkWriteDescriptorSet.pBufferInfo(writeDescriptorSet, MemorySegment.NULL);
        VkWriteDescriptorSet.pTexelBufferView(writeDescriptorSet, MemorySegment.NULL);
        
        Vulkan.updateDescriptorSets(device.handle(), 1, writeDescriptorSet, 0, MemorySegment.NULL);
    }
    
    /**
     * Binds this descriptor set to a command buffer
     */
    public void bind(MemorySegment commandBuffer, int pipelineBindPoint, MemorySegment pipelineLayout, int firstSet, Arena arena) {
        MemorySegment descriptorSets = arena.allocate(ValueLayout.ADDRESS);
        descriptorSets.set(ValueLayout.ADDRESS, 0, handle);
        Vulkan.cmdBindDescriptorSets(commandBuffer, pipelineBindPoint, pipelineLayout, firstSet, 1, descriptorSets, 0, MemorySegment.NULL);
    }
}