package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.buffers.ManagedBuffer;
import io.github.yetyman.vulkan.command.VkBind;
import io.github.yetyman.vulkan.enums.VkDescriptorType;
import io.github.yetyman.vulkan.enums.VkImageLayout;
import io.github.yetyman.vulkan.enums.VkPipelineBindPoint;
import io.github.yetyman.vulkan.enums.VkStructureType;
import io.github.yetyman.vulkan.generated.VkDescriptorBufferInfo;
import io.github.yetyman.vulkan.generated.VkDescriptorImageInfo;
import io.github.yetyman.vulkan.generated.VkWriteDescriptorSet;

import java.lang.foreign.*;

/**
 * Wrapper for Vulkan descriptor set (VkDescriptorSet).
 * Descriptor sets contain bindings to resources like buffers, images, and samplers.
 */
public class VkDescriptorSet {
    private final MemorySegment handle;
    private final VkDevice device;
    private final int[] bindingRequiredUsageBits;
    private final int[] bindingDescriptorTypes;

    public VkDescriptorSet(MemorySegment handle, VkDevice device) {
        this.handle = handle;
        this.device = device;
        this.bindingRequiredUsageBits = new int[0];
        this.bindingDescriptorTypes = new int[0];
    }

    public VkDescriptorSet(MemorySegment handle, VkDevice device, int[] bindingRequiredUsageBits, int[] bindingDescriptorTypes) {
        this.handle = handle;
        this.device = device;
        this.bindingRequiredUsageBits = bindingRequiredUsageBits;
        this.bindingDescriptorTypes = bindingDescriptorTypes;
    }

    /**
     * @return the VkDescriptorSet handle
     */
    public MemorySegment handle() {
        return handle;
    }

    /**
     * Updates this descriptor set to bind a uniform buffer
     */
    public void bindBuffer(int binding, int descriptorType, MemorySegment buffer, long offset, long range, SegmentAllocator allocator) {
        MemorySegment bufferInfo = VkDescriptorBufferInfo.allocate(allocator);
        VkDescriptorBufferInfo.buffer(bufferInfo, buffer);
        VkDescriptorBufferInfo.offset(bufferInfo, offset);
        VkDescriptorBufferInfo.range(bufferInfo, range);

        MemorySegment writeDescriptorSet = VkWriteDescriptorSet.allocate(allocator);
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
    public void updateImageSampler(int binding, MemorySegment sampler, MemorySegment imageView, int imageLayout, SegmentAllocator allocator) {
        updateImageSampler(binding, sampler, imageView, imageLayout, VkDescriptorType.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER.value(), allocator);
    }

    /**
     * Updates this descriptor set to bind an image with a specific descriptor type.
     * Use {@code VK_DESCRIPTOR_TYPE_STORAGE_IMAGE} for compute storage images (sampler must be NULL).
     * Use {@code VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER} for sampled images.
     */
    public void updateImageSampler(int binding, MemorySegment sampler, MemorySegment imageView, int imageLayout, int descriptorType, SegmentAllocator allocator) {
        MemorySegment imageInfo = VkDescriptorImageInfo.allocate(allocator);
        VkDescriptorImageInfo.sampler(imageInfo, sampler);
        VkDescriptorImageInfo.imageView(imageInfo, imageView);
        VkDescriptorImageInfo.imageLayout(imageInfo, imageLayout);

        MemorySegment writeDescriptorSet = VkWriteDescriptorSet.allocate(allocator);
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
    public void bind(MemorySegment commandBuffer, int pipelineBindPoint, MemorySegment pipelineLayout, int firstSet, SegmentAllocator allocator) {
        VkBind.bindDescriptorSets(commandBuffer, pipelineBindPoint, pipelineLayout, firstSet, handle);
    }

    /**
     * Binds this descriptor set for a compute pipeline.
     */
    public void bind(MemorySegment commandBuffer, VkComputePipeline pipeline, int firstSet, SegmentAllocator allocator) {
        bind(commandBuffer, VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_COMPUTE.value(), pipeline.layout(), firstSet, allocator);
    }

    /**
     * Binds this descriptor set for a graphics pipeline.
     */
    public void bind(MemorySegment commandBuffer, VkPipeline pipeline, int firstSet, SegmentAllocator allocator) {
        bind(commandBuffer, VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS.value(), pipeline.layout(), firstSet, allocator);
    }

    // High-level wrapper versions

    /**
     * Binds this descriptor set to a command buffer (low-level pipeline layout)
     */
    public void bind(VkCommandBuffer commandBuffer, int pipelineBindPoint, MemorySegment pipelineLayout, int firstSet, SegmentAllocator allocator) {
        bind(commandBuffer.handle(), pipelineBindPoint, pipelineLayout, firstSet, allocator);
    }

    /**
     * Binds this descriptor set for a compute pipeline (high-level)
     */
    public void bind(VkCommandBuffer commandBuffer, VkComputePipeline pipeline, int firstSet, SegmentAllocator allocator) {
        bind(commandBuffer.handle(), VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_COMPUTE.value(), pipeline.layout(), firstSet, allocator);
    }

    /**
     * Binds this descriptor set for a graphics pipeline (high-level)
     */
    public void bind(VkCommandBuffer commandBuffer, VkPipeline pipeline, int firstSet, SegmentAllocator allocator) {
        bind(commandBuffer.handle(), VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS.value(), pipeline.layout(), firstSet, allocator);
    }

    /**
     * Updates this descriptor set to bind a VkBuffer.
     * Validates the buffer's usage against the layout's required bit for this binding,
     * then uses the layout's stored descriptor type — no inference, no branching.
     *
     * @throws IllegalArgumentException if the buffer lacks the required usage bit for this binding
     */
    public void bind(int binding, VkBuffer buffer, long offset, long range, SegmentAllocator allocator) {
        int required = bindingRequiredUsageBits[binding];
        if ((buffer.usage() & required) == 0)
            throw new IllegalArgumentException("Buffer at binding " + binding + " lacks required usage bit 0x" + Integer.toHexString(required));
        int descriptorType = bindingDescriptorTypes[binding];
        bindBuffer(binding, descriptorType, buffer.handle(), offset, range, allocator);
    }

    /** Updates this descriptor set to bind a VkBuffer using the full buffer range. */
    public void bind(int binding, VkBuffer buffer, SegmentAllocator allocator) {
        bind(binding, buffer, 0, buffer.size(), allocator);
    }

    /**
     * Updates this descriptor set to bind a ManagedBuffer
     */
    public void bind(int binding, ManagedBuffer buffer, SegmentAllocator allocator) {
        bind(binding, buffer.vkBuffer(), 0, buffer.size(), allocator);
    }

    /**
     * Updates this descriptor set to bind a ManagedBuffer with offset and range
     */
    public void bind(int binding, ManagedBuffer buffer, long offset, long range, SegmentAllocator allocator) {
        bind(binding, buffer.vkBuffer(), offset, range, allocator);
    }

    /**
     * Updates this descriptor set to bind a combined image sampler
     */
    public void bind(int binding, VkImageView imageView, VkSampler sampler, SegmentAllocator allocator) {
        updateImageSampler(binding, sampler.handle(), imageView.handle(), VkImageLayout.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL.value(), allocator);
    }

    /**
     * Updates this descriptor set to bind a combined image sampler with custom layout
     */
    public void bind(int binding, VkImageView imageView, VkSampler sampler, int imageLayout, SegmentAllocator allocator) {
        updateImageSampler(binding, sampler.handle(), imageView.handle(), imageLayout, allocator);
    }

    /**
     * Updates this descriptor set to bind a storage image
     */
    public void bindStorage(int binding, VkImageView imageView, SegmentAllocator allocator) {
        updateImageSampler(binding, MemorySegment.NULL, imageView.handle(), VkImageLayout.VK_IMAGE_LAYOUT_GENERAL.value(), VkDescriptorType.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE.value(), allocator);
    }

    /**
     * Updates this descriptor set to bind a storage image with custom layout
     */
    public void bindStorage(int binding, VkImageView imageView, int imageLayout, SegmentAllocator allocator) {
        updateImageSampler(binding, MemorySegment.NULL, imageView.handle(), imageLayout, VkDescriptorType.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE.value(), allocator);
    }

    /**
     * Updates this descriptor set to bind a sampled image (without sampler)
     */
    public void bind(int binding, VkImageView imageView, SegmentAllocator allocator) {
        updateImageSampler(binding, MemorySegment.NULL, imageView.handle(), VkImageLayout.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL.value(), VkDescriptorType.VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE.value(), allocator);
    }

    /**
     * Updates this descriptor set to bind a sampled image with custom layout
     */
    public void bind(int binding, VkImageView imageView, int imageLayout, SegmentAllocator allocator) {
        updateImageSampler(binding, MemorySegment.NULL, imageView.handle(), imageLayout, VkDescriptorType.VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE.value(), allocator);
    }

}