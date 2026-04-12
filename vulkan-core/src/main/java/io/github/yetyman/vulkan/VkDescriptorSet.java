package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.buffers.ManagedBuffer;
import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;
import io.github.yetyman.vulkan.command.VkBind;
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
        updateImageSampler(binding, sampler, imageView, imageLayout, VkDescriptorType.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER.value(), arena);
    }

    /**
     * Updates this descriptor set to bind an image with a specific descriptor type.
     * Use {@code VK_DESCRIPTOR_TYPE_STORAGE_IMAGE} for compute storage images (sampler must be NULL).
     * Use {@code VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER} for sampled images.
     */
    public void updateImageSampler(int binding, MemorySegment sampler, MemorySegment imageView, int imageLayout, int descriptorType, Arena arena) {
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
        VkWriteDescriptorSet.descriptorType(writeDescriptorSet, descriptorType);
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
        VkBind.bindDescriptorSets(commandBuffer, pipelineBindPoint, pipelineLayout, firstSet, handle);
    }

    /** Binds this descriptor set for a compute pipeline. */
    public void bind(MemorySegment commandBuffer, VkComputePipeline pipeline, int firstSet, Arena arena) {
        bind(commandBuffer, VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_COMPUTE.value(), pipeline.layout(), firstSet, arena);
    }

    /** Binds this descriptor set for a graphics pipeline. */
    public void bind(MemorySegment commandBuffer, VkPipeline pipeline, int firstSet, Arena arena) {
        bind(commandBuffer, VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS.value(), pipeline.layout(), firstSet, arena);
    }
    
    // High-level wrapper versions
    
    /** Binds this descriptor set to a command buffer (low-level pipeline layout) */
    public void bind(VkCommandBuffer commandBuffer, int pipelineBindPoint, MemorySegment pipelineLayout, int firstSet, Arena arena) {
        bind(commandBuffer.handle(), pipelineBindPoint, pipelineLayout, firstSet, arena);
    }

    /** Binds this descriptor set for a compute pipeline (high-level) */
    public void bind(VkCommandBuffer commandBuffer, VkComputePipeline pipeline, int firstSet, Arena arena) {
        bind(commandBuffer.handle(), VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_COMPUTE.value(), pipeline.layout(), firstSet, arena);
    }

    /** Binds this descriptor set for a graphics pipeline (high-level) */
    public void bind(VkCommandBuffer commandBuffer, VkPipeline pipeline, int firstSet, Arena arena) {
        bind(commandBuffer.handle(), VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS.value(), pipeline.layout(), firstSet, arena);
    }

    // Wrapper-type bind overloads for ergonomic descriptor binding
    
    /** Updates this descriptor set to bind a VkBuffer */
    public void bind(int binding, VkBuffer buffer, Arena arena) {
        updateBuffer(binding, VkDescriptorType.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER.value(), buffer.handle(), 0, buffer.size(), arena);
    }
    
    /** Updates this descriptor set to bind a VkBuffer with offset and range */
    public void bind(int binding, VkBuffer buffer, long offset, long range, Arena arena) {
        updateBuffer(binding, VkDescriptorType.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER.value(), buffer.handle(), offset, range, arena);
    }
    
    /** Updates this descriptor set to bind a VkBuffer as storage buffer */
    public void bindStorage(int binding, VkBuffer buffer, Arena arena) {
        updateBuffer(binding, VkDescriptorType.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER.value(), buffer.handle(), 0, buffer.size(), arena);
    }
    
    /** Updates this descriptor set to bind a VkBuffer as storage buffer with offset and range */
    public void bindStorage(int binding, VkBuffer buffer, long offset, long range, Arena arena) {
        updateBuffer(binding, VkDescriptorType.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER.value(), buffer.handle(), offset, range, arena);
    }
    
    /** Updates this descriptor set to bind a ManagedBuffer */
    public void bind(int binding, ManagedBuffer buffer, Arena arena) {
        updateBuffer(binding, VkDescriptorType.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER.value(), buffer.handle(), 0, buffer.size(), arena);
    }
    
    /** Updates this descriptor set to bind a ManagedBuffer with offset and range */
    public void bind(int binding, ManagedBuffer buffer, long offset, long range, Arena arena) {
        updateBuffer(binding, VkDescriptorType.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER.value(), buffer.handle(), offset, range, arena);
    }
    
    /** Updates this descriptor set to bind a ManagedBuffer as storage buffer */
    public void bindStorage(int binding, ManagedBuffer buffer, Arena arena) {
        updateBuffer(binding, VkDescriptorType.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER.value(), buffer.handle(), 0, buffer.size(), arena);
    }
    
    /** Updates this descriptor set to bind a ManagedBuffer as storage buffer with offset and range */
    public void bindStorage(int binding, ManagedBuffer buffer, long offset, long range, Arena arena) {
        updateBuffer(binding, VkDescriptorType.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER.value(), buffer.handle(), offset, range, arena);
    }
    
    /** Updates this descriptor set to bind a combined image sampler */
    public void bind(int binding, VkImageView imageView, VkSampler sampler, Arena arena) {
        updateImageSampler(binding, sampler.handle(), imageView.handle(), VkImageLayout.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL.value(), arena);
    }
    
    /** Updates this descriptor set to bind a combined image sampler with custom layout */
    public void bind(int binding, VkImageView imageView, VkSampler sampler, int imageLayout, Arena arena) {
        updateImageSampler(binding, sampler.handle(), imageView.handle(), imageLayout, arena);
    }
    
    /** Updates this descriptor set to bind a storage image */
    public void bindStorage(int binding, VkImageView imageView, Arena arena) {
        updateImageSampler(binding, MemorySegment.NULL, imageView.handle(), VkImageLayout.VK_IMAGE_LAYOUT_GENERAL.value(), VkDescriptorType.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE.value(), arena);
    }
    
    /** Updates this descriptor set to bind a storage image with custom layout */
    public void bindStorage(int binding, VkImageView imageView, int imageLayout, Arena arena) {
        updateImageSampler(binding, MemorySegment.NULL, imageView.handle(), imageLayout, VkDescriptorType.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE.value(), arena);
    }
    
    /** Updates this descriptor set to bind a sampled image (without sampler) */
    public void bind(int binding, VkImageView imageView, Arena arena) {
        updateImageSampler(binding, MemorySegment.NULL, imageView.handle(), VkImageLayout.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL.value(), VkDescriptorType.VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE.value(), arena);
    }
    
    /** Updates this descriptor set to bind a sampled image with custom layout */
    public void bind(int binding, VkImageView imageView, int imageLayout, Arena arena) {
        updateImageSampler(binding, MemorySegment.NULL, imageView.handle(), imageLayout, VkDescriptorType.VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE.value(), arena);
    }

}