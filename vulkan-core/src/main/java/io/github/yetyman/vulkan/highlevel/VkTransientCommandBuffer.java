package io.github.yetyman.vulkan.highlevel;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;
import io.github.yetyman.vulkan.generated.VkBufferCopy;

import java.lang.foreign.*;
import java.util.function.Consumer;

/**
 * Transient command buffer for short-lived operations like buffer transfers.
 * Automatically handles allocation, recording, submission, and cleanup.
 * 
 * Example usage:
 * ```java
 * // Simple one-shot execution
 * VkTransientCommandBuffer.execute(commandPool, queue, arena, cmd -> {
 *     cmd.copyBuffer(srcBuffer, dstBuffer, size);
 * });
 * 
 * // Manual control
 * try (VkTransientCommandBuffer cmd = VkTransientCommandBuffer.begin(commandPool, queue, arena)) {
 *     cmd.transitionImageLayout(image, oldLayout, newLayout, srcStage, dstStage, srcAccess, dstAccess);
 *     cmd.copyBufferToImage(buffer, image, width, height);
 *     cmd.submitAndWait();
 * }
 * ```
 */
public class VkTransientCommandBuffer implements AutoCloseable {
    private final MemorySegment commandBuffer;
    private final VkCommandPool commandPool;
    private final MemorySegment queue;
    private final VkDevice device;
    private final Arena arena;
    private boolean recorded = false;
    private boolean submitted = false;
    
    private VkTransientCommandBuffer(MemorySegment commandBuffer, VkCommandPool commandPool, 
                                   MemorySegment queue, VkDevice device, Arena arena) {
        this.commandBuffer = commandBuffer;
        this.commandPool = commandPool;
        this.queue = queue;
        this.device = device;
        this.arena = arena;
    }
    
    /**
     * Creates a transient command buffer and begins recording.
     */
    public static VkTransientCommandBuffer begin(VkCommandPool commandPool, MemorySegment queue, Arena arena) {
        VkDevice device = commandPool.device();
        
        // Allocate command buffer
        MemorySegment allocInfo = VkCommandBufferAllocateInfo.allocate(arena);
        VkCommandBufferAllocateInfo.sType(allocInfo, VkStructureType.VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO.value());
        VkCommandBufferAllocateInfo.commandPool(allocInfo, commandPool.handle());
        VkCommandBufferAllocateInfo.level(allocInfo, VkCommandBufferLevel.VK_COMMAND_BUFFER_LEVEL_PRIMARY.value());
        VkCommandBufferAllocateInfo.commandBufferCount(allocInfo, 1);
        
        MemorySegment commandBufferPtr = arena.allocate(ValueLayout.ADDRESS);
        Vulkan.allocateCommandBuffers(device.handle(), allocInfo, commandBufferPtr).check();
        MemorySegment commandBuffer = commandBufferPtr.get(ValueLayout.ADDRESS, 0);
        
        // Begin recording
        MemorySegment beginInfo = VkCommandBufferBeginInfo.allocate(arena);
        VkCommandBufferBeginInfo.sType(beginInfo, VkStructureType.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO.value());
        VkCommandBufferBeginInfo.flags(beginInfo, VkCommandBufferUsageFlagBits.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT.value());
        
        Vulkan.beginCommandBuffer(commandBuffer, beginInfo).check();
        
        VkTransientCommandBuffer transient1 = new VkTransientCommandBuffer(commandBuffer, commandPool, queue, device, arena);
        transient1.recorded = true;
        return transient1;
    }
    
    /**
     * Executes a function with a transient command buffer and automatically submits it.
     */
    public static void execute(VkCommandPool commandPool, MemorySegment queue, Arena arena, 
                              Consumer<MemorySegment> commands) {
        try (VkTransientCommandBuffer cmd = begin(commandPool, queue, arena)) {
            commands.accept(cmd.commandBuffer);
            cmd.submitAndWait();
        }
    }
    
    /** @return the command buffer handle */
    public MemorySegment handle() { return commandBuffer; }
    
    /**
     * Ends recording and submits the command buffer, waiting for completion.
     */
    public void submitAndWait() {
        if (submitted) {
            throw new IllegalStateException("Command buffer already submitted");
        }
        
        // End recording
        Vulkan.endCommandBuffer(commandBuffer).check();
        
        // Create fence for synchronization
        MemorySegment fenceInfo = VkFenceCreateInfo.allocate(arena);
        VkFenceCreateInfo.sType(fenceInfo, VkStructureType.VK_STRUCTURE_TYPE_FENCE_CREATE_INFO.value());
        
        MemorySegment fencePtr = arena.allocate(ValueLayout.ADDRESS);
        Vulkan.createFence(device.handle(), fenceInfo, fencePtr).check();
        MemorySegment fence = fencePtr.get(ValueLayout.ADDRESS, 0);
        
        try {
            // Submit command buffer
            MemorySegment submitInfo = VkSubmitInfo.allocate(arena);
            VkSubmitInfo.sType(submitInfo, VkStructureType.VK_STRUCTURE_TYPE_SUBMIT_INFO.value());
            VkSubmitInfo.commandBufferCount(submitInfo, 1);
            
            MemorySegment commandBuffers = arena.allocate(ValueLayout.ADDRESS);
            commandBuffers.set(ValueLayout.ADDRESS, 0, commandBuffer);
            VkSubmitInfo.pCommandBuffers(submitInfo, commandBuffers);
            
            Vulkan.queueSubmit(queue, 1, submitInfo, fence).check();
            
            // Wait for completion
            MemorySegment fences = arena.allocate(ValueLayout.ADDRESS);
            fences.set(ValueLayout.ADDRESS, 0, fence);
            Vulkan.waitForFences(device.handle(), 1, fences, 1, Long.MAX_VALUE).check();
            
            submitted = true;
        } finally {
            Vulkan.destroyFence(device.handle(), fence);
        }
    }
    
    /**
     * Copies data from source buffer to destination buffer.
     */
    public void copyBuffer(MemorySegment srcBuffer, MemorySegment dstBuffer, long size) {
        copyBuffer(srcBuffer, dstBuffer, 0, 0, size);
    }
    
    /**
     * Copies data from source buffer to destination buffer with offsets.
     */
    public void copyBuffer(MemorySegment srcBuffer, MemorySegment dstBuffer, 
                          long srcOffset, long dstOffset, long size) {
        MemorySegment copyRegion = VkBufferCopy.allocate(arena);
        VkBufferCopy.srcOffset(copyRegion, srcOffset);
        VkBufferCopy.dstOffset(copyRegion, dstOffset);
        VkBufferCopy.size(copyRegion, size);
        
        Vulkan.cmdCopyBuffer(commandBuffer, srcBuffer, dstBuffer, 1, copyRegion);
    }
    
    /**
     * Copies data from buffer to image.
     */
    public void copyBufferToImage(MemorySegment buffer, MemorySegment image, int width, int height) {
        copyBufferToImage(buffer, image, width, height, 1);
    }
    
    /**
     * Copies data from buffer to image with depth.
     */
    public void copyBufferToImage(MemorySegment buffer, MemorySegment image, int width, int height, int depth) {
        MemorySegment region = VkBufferImageCopy.allocate(arena);
        VkBufferImageCopy.bufferOffset(region, 0);
        VkBufferImageCopy.bufferRowLength(region, 0);
        VkBufferImageCopy.bufferImageHeight(region, 0);
        
        MemorySegment imageSubresource = VkBufferImageCopy.imageSubresource(region);
        VkImageSubresourceLayers.aspectMask(imageSubresource, VkImageAspectFlagBits.VK_IMAGE_ASPECT_COLOR_BIT.value());
        VkImageSubresourceLayers.mipLevel(imageSubresource, 0);
        VkImageSubresourceLayers.baseArrayLayer(imageSubresource, 0);
        VkImageSubresourceLayers.layerCount(imageSubresource, 1);
        
        MemorySegment imageOffset = VkBufferImageCopy.imageOffset(region);
        VkOffset3D.x(imageOffset, 0);
        VkOffset3D.y(imageOffset, 0);
        VkOffset3D.z(imageOffset, 0);
        
        MemorySegment imageExtent = VkBufferImageCopy.imageExtent(region);
        VkExtent3D.width(imageExtent, width);
        VkExtent3D.height(imageExtent, height);
        VkExtent3D.depth(imageExtent, depth);
        
        Vulkan.cmdCopyBufferToImage(commandBuffer, buffer, image, 
            VkImageLayout.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL.value(), 1, region);
    }
    
    /**
     * Transitions image layout using a pipeline barrier.
     */
    public void transitionImageLayout(MemorySegment image, int oldLayout, int newLayout,
                                     int srcStageMask, int dstStageMask, int srcAccessMask, int dstAccessMask) {
        MemorySegment barrier = VkImageMemoryBarrier.allocate(arena);
        VkImageMemoryBarrier.sType(barrier, VkStructureType.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER.value());
        VkImageMemoryBarrier.oldLayout(barrier, oldLayout);
        VkImageMemoryBarrier.newLayout(barrier, newLayout);
        VkImageMemoryBarrier.srcQueueFamilyIndex(barrier, 0xFFFFFFFF);
        VkImageMemoryBarrier.dstQueueFamilyIndex(barrier, 0xFFFFFFFF);
        VkImageMemoryBarrier.image(barrier, image);
        VkImageMemoryBarrier.srcAccessMask(barrier, srcAccessMask);
        VkImageMemoryBarrier.dstAccessMask(barrier, dstAccessMask);
        
        MemorySegment subresourceRange = VkImageMemoryBarrier.subresourceRange(barrier);
        VkImageSubresourceRange.aspectMask(subresourceRange, VkImageAspectFlagBits.VK_IMAGE_ASPECT_COLOR_BIT.value());
        VkImageSubresourceRange.baseMipLevel(subresourceRange, 0);
        VkImageSubresourceRange.levelCount(subresourceRange, 1);
        VkImageSubresourceRange.baseArrayLayer(subresourceRange, 0);
        VkImageSubresourceRange.layerCount(subresourceRange, 1);
        
        Vulkan.cmdPipelineBarrier(commandBuffer, srcStageMask, dstStageMask, 0,
            0, MemorySegment.NULL, 0, MemorySegment.NULL, 1, barrier);
    }
    
    /**
     * Records a memory barrier.
     */
    public void memoryBarrier(int srcStageMask, int dstStageMask, int srcAccessMask, int dstAccessMask) {
        MemorySegment barrier = io.github.yetyman.vulkan.generated.VkMemoryBarrier.allocate(arena);
        io.github.yetyman.vulkan.generated.VkMemoryBarrier.sType(barrier, VkStructureType.VK_STRUCTURE_TYPE_MEMORY_BARRIER.value());
        io.github.yetyman.vulkan.generated.VkMemoryBarrier.srcAccessMask(barrier, srcAccessMask);
        io.github.yetyman.vulkan.generated.VkMemoryBarrier.dstAccessMask(barrier, dstAccessMask);
        
        Vulkan.cmdPipelineBarrier(commandBuffer, srcStageMask, dstStageMask, 0,
            1, barrier, 0, MemorySegment.NULL, 0, MemorySegment.NULL);
    }
    
    @Override
    public void close() {
        if (recorded && !submitted) {
            try {
                Vulkan.endCommandBuffer(commandBuffer);
            } catch (Exception e) {
                // Ignore errors during cleanup
            }
        }
        
        // Free command buffer
        MemorySegment commandBuffers = arena.allocate(ValueLayout.ADDRESS);
        commandBuffers.set(ValueLayout.ADDRESS, 0, commandBuffer);
        Vulkan.freeCommandBuffers(device.handle(), commandPool.handle(), 1, commandBuffers);
    }
}