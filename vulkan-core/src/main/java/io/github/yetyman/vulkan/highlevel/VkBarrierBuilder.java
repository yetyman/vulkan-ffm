package io.github.yetyman.vulkan.highlevel;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;
import java.util.*;

/**
 * Fluent API for building complex pipeline barriers with multiple memory, buffer, and image barriers.
 * 
 * Example usage:
 * <pre>{@code
 * // Texture upload barrier
 * VkBarrierBuilder.create(arena)
 *     .transitionToTransferDst(textureImage)
 *     .record(commandBuffer);
 * 
 * // Multiple resource synchronization
 * VkBarrierBuilder.create(arena)
 *     .srcStage(VK_PIPELINE_STAGE_TRANSFER_BIT)
 *     .dstStage(VK_PIPELINE_STAGE_VERTEX_SHADER_BIT | VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT)
 *     .vertexBufferBarrier(vertexBuffer)
 *     .uniformBufferBarrier(uniformBuffer)
 *     .transitionToShaderRead(textureImage)
 *     .record(commandBuffer);
 * }</pre>
 */
public class VkBarrierBuilder {
    private final Arena arena;
    private final List<MemorySegment> memoryBarriers = new ArrayList<>();
    private final List<MemorySegment> bufferBarriers = new ArrayList<>();
    private final List<MemorySegment> imageBarriers = new ArrayList<>();
    private int srcStageMask = 0;
    private int dstStageMask = 0;
    private int dependencyFlags = 0;
    
    private VkBarrierBuilder(Arena arena) {
        this.arena = arena;
    }
    
    public static VkBarrierBuilder create(Arena arena) {
        return new VkBarrierBuilder(arena);
    }
    
    /**
     * Sets source pipeline stage mask.
     */
    public VkBarrierBuilder srcStage(int stageMask) {
        this.srcStageMask = stageMask;
        return this;
    }
    
    /**
     * Sets destination pipeline stage mask.
     */
    public VkBarrierBuilder dstStage(int stageMask) {
        this.dstStageMask = stageMask;
        return this;
    }
    
    /**
     * Sets dependency flags.
     */
    public VkBarrierBuilder dependencyFlags(int flags) {
        this.dependencyFlags = flags;
        return this;
    }
    
    /**
     * Adds a memory barrier.
     */
    public VkBarrierBuilder memoryBarrier(int srcAccessMask, int dstAccessMask) {
        MemorySegment barrier = VkMemoryBarrier.allocate(arena);
        VkMemoryBarrier.sType(barrier, VkStructureType.VK_STRUCTURE_TYPE_MEMORY_BARRIER);
        VkMemoryBarrier.srcAccessMask(barrier, srcAccessMask);
        VkMemoryBarrier.dstAccessMask(barrier, dstAccessMask);
        memoryBarriers.add(barrier);
        return this;
    }
    
    /**
     * Adds a buffer memory barrier.
     */
    public VkBarrierBuilder bufferBarrier(MemorySegment buffer, int srcAccessMask, int dstAccessMask) {
        return bufferBarrier(buffer, srcAccessMask, dstAccessMask, 
            0xFFFFFFFF, 0xFFFFFFFF, 0, -1);
    }
    
    /**
     * Adds a buffer memory barrier with queue family transfer.
     */
    public VkBarrierBuilder bufferBarrier(MemorySegment buffer, int srcAccessMask, int dstAccessMask,
                                         int srcQueueFamily, int dstQueueFamily, long offset, long size) {
        MemorySegment barrier = VkBufferMemoryBarrier.allocate(arena);
        VkBufferMemoryBarrier.sType(barrier, VkStructureType.VK_STRUCTURE_TYPE_BUFFER_MEMORY_BARRIER);
        VkBufferMemoryBarrier.srcAccessMask(barrier, srcAccessMask);
        VkBufferMemoryBarrier.dstAccessMask(barrier, dstAccessMask);
        VkBufferMemoryBarrier.srcQueueFamilyIndex(barrier, srcQueueFamily);
        VkBufferMemoryBarrier.dstQueueFamilyIndex(barrier, dstQueueFamily);
        VkBufferMemoryBarrier.buffer(barrier, buffer);
        VkBufferMemoryBarrier.offset(barrier, offset);
        VkBufferMemoryBarrier.size(barrier, size == -1 ? 0xFFFFFFFFFFFFFFFFL : size);
        bufferBarriers.add(barrier);
        return this;
    }
    
    /**
     * Adds an image layout transition barrier.
     */
    public VkBarrierBuilder imageBarrier(MemorySegment image, int srcAccessMask, int dstAccessMask,
                                        int oldLayout, int newLayout) {
        return imageBarrier(image, srcAccessMask, dstAccessMask, oldLayout, newLayout,
            0xFFFFFFFF, 0xFFFFFFFF,
            VkImageAspectFlagBits.VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1);
    }
    
    /**
     * Adds a complete image memory barrier.
     */
    public VkBarrierBuilder imageBarrier(MemorySegment image, int srcAccessMask, int dstAccessMask,
                                        int oldLayout, int newLayout, int srcQueueFamily, int dstQueueFamily,
                                        int aspectMask, int baseMipLevel, int levelCount, 
                                        int baseArrayLayer, int layerCount) {
        MemorySegment barrier = VkImageMemoryBarrier.allocate(arena);
        VkImageMemoryBarrier.sType(barrier, VkStructureType.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER);
        VkImageMemoryBarrier.srcAccessMask(barrier, srcAccessMask);
        VkImageMemoryBarrier.dstAccessMask(barrier, dstAccessMask);
        VkImageMemoryBarrier.oldLayout(barrier, oldLayout);
        VkImageMemoryBarrier.newLayout(barrier, newLayout);
        VkImageMemoryBarrier.srcQueueFamilyIndex(barrier, srcQueueFamily);
        VkImageMemoryBarrier.dstQueueFamilyIndex(barrier, dstQueueFamily);
        VkImageMemoryBarrier.image(barrier, image);
        
        MemorySegment subresourceRange = VkImageMemoryBarrier.subresourceRange(barrier);
        VkImageSubresourceRange.aspectMask(subresourceRange, aspectMask);
        VkImageSubresourceRange.baseMipLevel(subresourceRange, baseMipLevel);
        VkImageSubresourceRange.levelCount(subresourceRange, levelCount);
        VkImageSubresourceRange.baseArrayLayer(subresourceRange, baseArrayLayer);
        VkImageSubresourceRange.layerCount(subresourceRange, layerCount);
        
        imageBarriers.add(barrier);
        return this;
    }
    
    /**
     * Common barrier for transitioning image from undefined to transfer destination.
     */
    public VkBarrierBuilder transitionToTransferDst(MemorySegment image) {
        srcStage(VkPipelineStageFlagBits.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT);
        dstStage(VkPipelineStageFlagBits.VK_PIPELINE_STAGE_TRANSFER_BIT);
        return imageBarrier(image, 0, VkAccessFlagBits.VK_ACCESS_TRANSFER_WRITE_BIT,
            VkImageLayout.VK_IMAGE_LAYOUT_UNDEFINED, VkImageLayout.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
    }
    
    /**
     * Common barrier for transitioning image from transfer destination to shader read.
     */
    public VkBarrierBuilder transitionToShaderRead(MemorySegment image) {
        srcStage(VkPipelineStageFlagBits.VK_PIPELINE_STAGE_TRANSFER_BIT);
        dstStage(VkPipelineStageFlagBits.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT);
        return imageBarrier(image, VkAccessFlagBits.VK_ACCESS_TRANSFER_WRITE_BIT, 
            VkAccessFlagBits.VK_ACCESS_SHADER_READ_BIT,
            VkImageLayout.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 
            VkImageLayout.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
    }
    
    /**
     * Common barrier for transitioning image to color attachment.
     */
    public VkBarrierBuilder transitionToColorAttachment(MemorySegment image) {
        srcStage(VkPipelineStageFlagBits.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT);
        dstStage(VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);
        return imageBarrier(image, 0, VkAccessFlagBits.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT,
            VkImageLayout.VK_IMAGE_LAYOUT_UNDEFINED, VkImageLayout.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
    }
    
    /**
     * Common barrier for transitioning image to depth attachment.
     */
    public VkBarrierBuilder transitionToDepthAttachment(MemorySegment image) {
        srcStage(VkPipelineStageFlagBits.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT);
        dstStage(VkPipelineStageFlagBits.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT);
        return imageBarrier(image, 0, 
            VkAccessFlagBits.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT | 
            VkAccessFlagBits.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT,
            VkImageLayout.VK_IMAGE_LAYOUT_UNDEFINED, 
            VkImageLayout.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
    }
    
    /**
     * Barrier for vertex buffer access.
     */
    public VkBarrierBuilder vertexBufferBarrier(MemorySegment buffer) {
        srcStage(VkPipelineStageFlagBits.VK_PIPELINE_STAGE_TRANSFER_BIT);
        dstStage(VkPipelineStageFlagBits.VK_PIPELINE_STAGE_VERTEX_INPUT_BIT);
        return bufferBarrier(buffer, VkAccessFlagBits.VK_ACCESS_TRANSFER_WRITE_BIT,
            VkAccessFlagBits.VK_ACCESS_VERTEX_ATTRIBUTE_READ_BIT);
    }
    
    /**
     * Barrier for index buffer access.
     */
    public VkBarrierBuilder indexBufferBarrier(MemorySegment buffer) {
        srcStage(VkPipelineStageFlagBits.VK_PIPELINE_STAGE_TRANSFER_BIT);
        dstStage(VkPipelineStageFlagBits.VK_PIPELINE_STAGE_VERTEX_INPUT_BIT);
        return bufferBarrier(buffer, VkAccessFlagBits.VK_ACCESS_TRANSFER_WRITE_BIT,
            VkAccessFlagBits.VK_ACCESS_INDEX_READ_BIT);
    }
    
    /**
     * Barrier for uniform buffer access.
     */
    public VkBarrierBuilder uniformBufferBarrier(MemorySegment buffer) {
        srcStage(VkPipelineStageFlagBits.VK_PIPELINE_STAGE_TRANSFER_BIT);
        dstStage(VkPipelineStageFlagBits.VK_PIPELINE_STAGE_VERTEX_SHADER_BIT | 
                VkPipelineStageFlagBits.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT);
        return bufferBarrier(buffer, VkAccessFlagBits.VK_ACCESS_TRANSFER_WRITE_BIT,
            VkAccessFlagBits.VK_ACCESS_UNIFORM_READ_BIT);
    }
    
    /**
     * Records the pipeline barrier to a command buffer.
     */
    public void record(MemorySegment commandBuffer) {
        MemorySegment memBarriersArray = MemorySegment.NULL;
        MemorySegment bufBarriersArray = MemorySegment.NULL;
        MemorySegment imgBarriersArray = MemorySegment.NULL;
        
        if (!memoryBarriers.isEmpty()) {
            memBarriersArray = arena.allocate(VkMemoryBarrier.layout(), memoryBarriers.size());
            for (int i = 0; i < memoryBarriers.size(); i++) {
                MemorySegment.copy(memoryBarriers.get(i), 0, memBarriersArray,
                    i * VkMemoryBarrier.layout().byteSize(), VkMemoryBarrier.layout().byteSize());
            }
        }
        
        if (!bufferBarriers.isEmpty()) {
            bufBarriersArray = arena.allocate(VkBufferMemoryBarrier.layout(), bufferBarriers.size());
            for (int i = 0; i < bufferBarriers.size(); i++) {
                MemorySegment.copy(bufferBarriers.get(i), 0, bufBarriersArray,
                    i * VkBufferMemoryBarrier.layout().byteSize(), VkBufferMemoryBarrier.layout().byteSize());
            }
        }
        
        if (!imageBarriers.isEmpty()) {
            imgBarriersArray = arena.allocate(VkImageMemoryBarrier.layout(), imageBarriers.size());
            for (int i = 0; i < imageBarriers.size(); i++) {
                MemorySegment.copy(imageBarriers.get(i), 0, imgBarriersArray,
                    i * VkImageMemoryBarrier.layout().byteSize(), VkImageMemoryBarrier.layout().byteSize());
            }
        }
        
        VulkanExtensions.cmdPipelineBarrier(commandBuffer, srcStageMask, dstStageMask, dependencyFlags,
            memoryBarriers.size(), memBarriersArray,
            bufferBarriers.size(), bufBarriersArray,
            imageBarriers.size(), imgBarriersArray);
    }
}