package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.generated.VulkanFFM;
import java.lang.foreign.*;

/**
 * Extended Vulkan function wrappers for operations not in the base Vulkan class.
 */
public class VulkanExtensions {
    
    public static VkResult createBuffer(MemorySegment device, MemorySegment createInfo, MemorySegment buffer) {
        int result = VulkanFFM.vkCreateBuffer(device, createInfo, MemorySegment.NULL, buffer);
        return VkResult.fromInt(result);
    }
    
    public static void destroyBuffer(MemorySegment device, MemorySegment buffer) {
        VulkanFFM.vkDestroyBuffer(device, buffer, MemorySegment.NULL);
    }
    
    public static VkResult allocateMemory(MemorySegment device, MemorySegment allocateInfo, MemorySegment memory) {
        int result = VulkanFFM.vkAllocateMemory(device, allocateInfo, MemorySegment.NULL, memory);
        return VkResult.fromInt(result);
    }
    
    public static void freeMemory(MemorySegment device, MemorySegment memory) {
        VulkanFFM.vkFreeMemory(device, memory, MemorySegment.NULL);
    }
    
    public static VkResult bindBufferMemory(MemorySegment device, MemorySegment buffer, MemorySegment memory, long offset) {
        int result = VulkanFFM.vkBindBufferMemory(device, buffer, memory, offset);
        return VkResult.fromInt(result);
    }
    
    public static void getBufferMemoryRequirements(MemorySegment device, MemorySegment buffer, MemorySegment requirements) {
        VulkanFFM.vkGetBufferMemoryRequirements(device, buffer, requirements);
    }
    
    public static void getPhysicalDeviceMemoryProperties(MemorySegment physicalDevice, MemorySegment properties) {
        VulkanFFM.vkGetPhysicalDeviceMemoryProperties(physicalDevice, properties);
    }
    
    public static VkResult mapMemory(MemorySegment device, MemorySegment memory, long offset, long size, int flags, MemorySegment data) {
        int result = VulkanFFM.vkMapMemory(device, memory, offset, size, flags, data);
        return VkResult.fromInt(result);
    }
    
    public static void unmapMemory(MemorySegment device, MemorySegment memory) {
        VulkanFFM.vkUnmapMemory(device, memory);
    }
    
    public static VkResult flushMappedMemoryRanges(MemorySegment device, int rangeCount, MemorySegment ranges) {
        int result = VulkanFFM.vkFlushMappedMemoryRanges(device, rangeCount, ranges);
        return VkResult.fromInt(result);
    }
    
    public static VkResult invalidateMappedMemoryRanges(MemorySegment device, int rangeCount, MemorySegment ranges) {
        int result = VulkanFFM.vkInvalidateMappedMemoryRanges(device, rangeCount, ranges);
        return VkResult.fromInt(result);
    }
    
    public static VkResult createImage(MemorySegment device, MemorySegment createInfo, MemorySegment image) {
        int result = VulkanFFM.vkCreateImage(device, createInfo, MemorySegment.NULL, image);
        return VkResult.fromInt(result);
    }
    
    public static void destroyImage(MemorySegment device, MemorySegment image) {
        VulkanFFM.vkDestroyImage(device, image, MemorySegment.NULL);
    }
    
    public static void getImageMemoryRequirements(MemorySegment device, MemorySegment image, MemorySegment requirements) {
        VulkanFFM.vkGetImageMemoryRequirements(device, image, requirements);
    }
    
    public static VkResult bindImageMemory(MemorySegment device, MemorySegment image, MemorySegment memory, long offset) {
        int result = VulkanFFM.vkBindImageMemory(device, image, memory, offset);
        return VkResult.fromInt(result);
    }
    
    public static VkResult createImageView(MemorySegment device, MemorySegment createInfo, MemorySegment imageView) {
        int result = VulkanFFM.vkCreateImageView(device, createInfo, MemorySegment.NULL, imageView);
        return VkResult.fromInt(result);
    }
    
    public static void destroyImageView(MemorySegment device, MemorySegment imageView) {
        VulkanFFM.vkDestroyImageView(device, imageView, MemorySegment.NULL);
    }
    
    public static VkResult createSampler(MemorySegment device, MemorySegment createInfo, MemorySegment sampler) {
        int result = VulkanFFM.vkCreateSampler(device, createInfo, MemorySegment.NULL, sampler);
        return VkResult.fromInt(result);
    }
    
    public static void destroySampler(MemorySegment device, MemorySegment sampler) {
        VulkanFFM.vkDestroySampler(device, sampler, MemorySegment.NULL);
    }
    
    public static VkResult createDescriptorPool(MemorySegment device, MemorySegment createInfo, MemorySegment pool) {
        int result = VulkanFFM.vkCreateDescriptorPool(device, createInfo, MemorySegment.NULL, pool);
        return VkResult.fromInt(result);
    }
    
    public static void destroyDescriptorPool(MemorySegment device, MemorySegment pool) {
        VulkanFFM.vkDestroyDescriptorPool(device, pool, MemorySegment.NULL);
    }
    
    public static VkResult allocateDescriptorSets(MemorySegment device, MemorySegment allocateInfo, MemorySegment sets) {
        int result = VulkanFFM.vkAllocateDescriptorSets(device, allocateInfo, sets);
        return VkResult.fromInt(result);
    }
    
    public static void updateDescriptorSets(MemorySegment device, int writeCount, MemorySegment writes, int copyCount, MemorySegment copies) {
        VulkanFFM.vkUpdateDescriptorSets(device, writeCount, writes, copyCount, copies);
    }
    
    public static VkResult createPipelineLayout(MemorySegment device, MemorySegment createInfo, MemorySegment layout) {
        int result = VulkanFFM.vkCreatePipelineLayout(device, createInfo, MemorySegment.NULL, layout);
        return VkResult.fromInt(result);
    }
    
    public static void destroyPipelineLayout(MemorySegment device, MemorySegment layout) {
        VulkanFFM.vkDestroyPipelineLayout(device, layout, MemorySegment.NULL);
    }
    
    public static VkResult createGraphicsPipelines(MemorySegment device, MemorySegment cache, int count, MemorySegment createInfos, MemorySegment pipelines) {
        int result = VulkanFFM.vkCreateGraphicsPipelines(device, cache, count, createInfos, MemorySegment.NULL, pipelines);
        return VkResult.fromInt(result);
    }
    
    public static VkResult createComputePipelines(MemorySegment device, MemorySegment cache, int count, MemorySegment createInfos, MemorySegment pipelines) {
        int result = VulkanFFM.vkCreateComputePipelines(device, cache, count, createInfos, MemorySegment.NULL, pipelines);
        return VkResult.fromInt(result);
    }
    
    public static void destroyPipeline(MemorySegment device, MemorySegment pipeline) {
        VulkanFFM.vkDestroyPipeline(device, pipeline, MemorySegment.NULL);
    }
    
    public static VkResult createFramebuffer(MemorySegment device, MemorySegment createInfo, MemorySegment framebuffer) {
        int result = VulkanFFM.vkCreateFramebuffer(device, createInfo, MemorySegment.NULL, framebuffer);
        return VkResult.fromInt(result);
    }
    
    public static void destroyFramebuffer(MemorySegment device, MemorySegment framebuffer) {
        VulkanFFM.vkDestroyFramebuffer(device, framebuffer, MemorySegment.NULL);
    }
    
    public static VkResult createSemaphore(MemorySegment device, MemorySegment createInfo, MemorySegment semaphore) {
        int result = VulkanFFM.vkCreateSemaphore(device, createInfo, MemorySegment.NULL, semaphore);
        return VkResult.fromInt(result);
    }
    
    public static void destroySemaphore(MemorySegment device, MemorySegment semaphore) {
        VulkanFFM.vkDestroySemaphore(device, semaphore, MemorySegment.NULL);
    }
    
    public static VkResult createFence(MemorySegment device, MemorySegment createInfo, MemorySegment fence) {
        int result = VulkanFFM.vkCreateFence(device, createInfo, MemorySegment.NULL, fence);
        return VkResult.fromInt(result);
    }
    
    public static void destroyFence(MemorySegment device, MemorySegment fence) {
        VulkanFFM.vkDestroyFence(device, fence, MemorySegment.NULL);
    }
    
    public static VkResult waitForFences(MemorySegment device, int fenceCount, MemorySegment fences, int waitAll, long timeout) {
        int result = VulkanFFM.vkWaitForFences(device, fenceCount, fences, waitAll, timeout);
        return VkResult.fromInt(result);
    }
    
    public static VkResult resetFences(MemorySegment device, int fenceCount, MemorySegment fences) {
        int result = VulkanFFM.vkResetFences(device, fenceCount, fences);
        return VkResult.fromInt(result);
    }
    
    public static VkResult allocateCommandBuffers(MemorySegment device, MemorySegment allocateInfo, MemorySegment commandBuffers) {
        int result = VulkanFFM.vkAllocateCommandBuffers(device, allocateInfo, commandBuffers);
        return VkResult.fromInt(result);
    }
    
    public static void freeCommandBuffers(MemorySegment device, MemorySegment commandPool, int count, MemorySegment commandBuffers) {
        VulkanFFM.vkFreeCommandBuffers(device, commandPool, count, commandBuffers);
    }
    
    public static VkResult beginCommandBuffer(MemorySegment commandBuffer, MemorySegment beginInfo) {
        int result = VulkanFFM.vkBeginCommandBuffer(commandBuffer, beginInfo);
        return VkResult.fromInt(result);
    }
    
    public static VkResult endCommandBuffer(MemorySegment commandBuffer) {
        int result = VulkanFFM.vkEndCommandBuffer(commandBuffer);
        return VkResult.fromInt(result);
    }
    
    public static VkResult queueSubmit(MemorySegment queue, int submitCount, MemorySegment submits, MemorySegment fence) {
        int result = VulkanFFM.vkQueueSubmit(queue, submitCount, submits, fence);
        return VkResult.fromInt(result);
    }
    
    public static void cmdPipelineBarrier(MemorySegment commandBuffer, int srcStageMask, int dstStageMask, int dependencyFlags,
                                         int memoryBarrierCount, MemorySegment memoryBarriers,
                                         int bufferMemoryBarrierCount, MemorySegment bufferMemoryBarriers,
                                         int imageMemoryBarrierCount, MemorySegment imageMemoryBarriers) {
        VulkanFFM.vkCmdPipelineBarrier(commandBuffer, srcStageMask, dstStageMask, dependencyFlags,
            memoryBarrierCount, memoryBarriers, bufferMemoryBarrierCount, bufferMemoryBarriers,
            imageMemoryBarrierCount, imageMemoryBarriers);
    }
    
    public static void cmdCopyBufferToImage(MemorySegment commandBuffer, MemorySegment srcBuffer, MemorySegment dstImage, int dstImageLayout, int regionCount, MemorySegment regions) {
        VulkanFFM.vkCmdCopyBufferToImage(commandBuffer, srcBuffer, dstImage, dstImageLayout, regionCount, regions);
    }
    
    public static void cmdBlitImage(MemorySegment commandBuffer, MemorySegment srcImage, int srcImageLayout, MemorySegment dstImage, int dstImageLayout, int regionCount, MemorySegment regions, int filter) {
        VulkanFFM.vkCmdBlitImage(commandBuffer, srcImage, srcImageLayout, dstImage, dstImageLayout, regionCount, regions, filter);
    }
    
    public static void cmdBindPipeline(MemorySegment commandBuffer, int pipelineBindPoint, MemorySegment pipeline) {
        VulkanFFM.vkCmdBindPipeline(commandBuffer, pipelineBindPoint, pipeline);
    }
    
    public static void cmdDispatch(MemorySegment commandBuffer, int groupCountX, int groupCountY, int groupCountZ) {
        VulkanFFM.vkCmdDispatch(commandBuffer, groupCountX, groupCountY, groupCountZ);
    }
    
    public static void cmdBindDescriptorSets(MemorySegment commandBuffer, int pipelineBindPoint, MemorySegment layout, int firstSet, int descriptorSetCount, MemorySegment descriptorSets, int dynamicOffsetCount, MemorySegment dynamicOffsets) {
        VulkanFFM.vkCmdBindDescriptorSets(commandBuffer, pipelineBindPoint, layout, firstSet, descriptorSetCount, descriptorSets, dynamicOffsetCount, dynamicOffsets);
    }
    
    public static void cmdPushConstants(MemorySegment commandBuffer, MemorySegment layout, int stageFlags, int offset, int size, MemorySegment values) {
        VulkanFFM.vkCmdPushConstants(commandBuffer, layout, stageFlags, offset, size, values);
    }
    
    public static void cmdBindVertexBuffers(MemorySegment commandBuffer, int firstBinding, int bindingCount, MemorySegment buffers, MemorySegment offsets) {
        VulkanFFM.vkCmdBindVertexBuffers(commandBuffer, firstBinding, bindingCount, buffers, offsets);
    }
    
    public static void cmdBindIndexBuffer(MemorySegment commandBuffer, MemorySegment buffer, long offset, int indexType) {
        VulkanFFM.vkCmdBindIndexBuffer(commandBuffer, buffer, offset, indexType);
    }
    
    public static void cmdDraw(MemorySegment commandBuffer, int vertexCount, int instanceCount, int firstVertex, int firstInstance) {
        VulkanFFM.vkCmdDraw(commandBuffer, vertexCount, instanceCount, firstVertex, firstInstance);
    }
    
    public static void cmdDrawIndexed(MemorySegment commandBuffer, int indexCount, int instanceCount, int firstIndex, int vertexOffset, int firstInstance) {
        VulkanFFM.vkCmdDrawIndexed(commandBuffer, indexCount, instanceCount, firstIndex, vertexOffset, firstInstance);
    }
    
    public static void cmdBeginRenderPass(MemorySegment commandBuffer, MemorySegment renderPassBegin, int contents) {
        VulkanFFM.vkCmdBeginRenderPass(commandBuffer, renderPassBegin, contents);
    }
    
    public static void cmdEndRenderPass(MemorySegment commandBuffer) {
        VulkanFFM.vkCmdEndRenderPass(commandBuffer);
    }
    
    // Timeline semaphore extensions (simplified)
    public static VkResult getSemaphoreCounterValue(MemorySegment device, MemorySegment semaphore, MemorySegment value) {
        // Simplified - would need actual extension function
        return VkResult.VK_SUCCESS;
    }
    
    public static VkResult waitSemaphores(MemorySegment device, MemorySegment waitInfo, long timeout) {
        // Simplified - would need actual extension function
        return VkResult.VK_SUCCESS;
    }
    
    public static VkResult signalSemaphore(MemorySegment device, MemorySegment signalInfo) {
        // Simplified - would need actual extension function
        return VkResult.VK_SUCCESS;
    }
    
    // Debug utils extensions (simplified)
    public static VkResult createDebugUtilsMessengerEXT(MemorySegment instance, MemorySegment createInfo, MemorySegment messenger) {
        // Simplified - would need actual extension function
        return VkResult.VK_SUCCESS;
    }
    
    public static void destroyDebugUtilsMessengerEXT(MemorySegment instance, MemorySegment messenger) {
        // Simplified - would need actual extension function
    }
    
    // Missing Vulkan extension functions
    public static VkResult acquireNextImageKHR(MemorySegment device, MemorySegment swapchain, long timeout, MemorySegment semaphore, MemorySegment fence, MemorySegment imageIndex) {
        int result = VulkanFFM.vkAcquireNextImageKHR(device, swapchain, timeout, semaphore, fence, imageIndex);
        return VkResult.fromInt(result);
    }
    
    public static void destroySurfaceKHR(MemorySegment instance, MemorySegment surface) {
        VulkanFFM.vkDestroySurfaceKHR(instance, surface, MemorySegment.NULL);
    }
    
    public static void destroyCommandPool(MemorySegment device, MemorySegment commandPool) {
        VulkanFFM.vkDestroyCommandPool(device, commandPool, MemorySegment.NULL);
    }
    
    public static VkResult createCommandPool(MemorySegment device, MemorySegment createInfo, MemorySegment commandPool) {
        int result = VulkanFFM.vkCreateCommandPool(device, createInfo, MemorySegment.NULL, commandPool);
        return VkResult.fromInt(result);
    }
    
    public static void destroySwapchainKHR(MemorySegment device, MemorySegment swapchain) {
        VulkanFFM.vkDestroySwapchainKHR(device, swapchain, MemorySegment.NULL);
    }
    
    public static VkResult createSwapchainKHR(MemorySegment device, MemorySegment createInfo, MemorySegment swapchain) {
        int result = VulkanFFM.vkCreateSwapchainKHR(device, createInfo, MemorySegment.NULL, swapchain);
        return VkResult.fromInt(result);
    }
    
    public static VkResult getSwapchainImagesKHR(MemorySegment device, MemorySegment swapchain, MemorySegment imageCount, MemorySegment images) {
        int result = VulkanFFM.vkGetSwapchainImagesKHR(device, swapchain, imageCount, images);
        return VkResult.fromInt(result);
    }
    
    public static void destroyRenderPass(MemorySegment device, MemorySegment renderPass) {
        VulkanFFM.vkDestroyRenderPass(device, renderPass, MemorySegment.NULL);
    }
    
    public static VkResult createRenderPass(MemorySegment device, MemorySegment createInfo, MemorySegment renderPass) {
        int result = VulkanFFM.vkCreateRenderPass(device, createInfo, MemorySegment.NULL, renderPass);
        return VkResult.fromInt(result);
    }
    
    public static void destroyDescriptorSetLayout(MemorySegment device, MemorySegment descriptorSetLayout) {
        VulkanFFM.vkDestroyDescriptorSetLayout(device, descriptorSetLayout, MemorySegment.NULL);
    }
    
    public static VkResult createDescriptorSetLayout(MemorySegment device, MemorySegment createInfo, MemorySegment setLayout) {
        int result = VulkanFFM.vkCreateDescriptorSetLayout(device, createInfo, MemorySegment.NULL, setLayout);
        return VkResult.fromInt(result);
    }
    
    public static void destroyShaderModule(MemorySegment device, MemorySegment shaderModule) {
        VulkanFFM.vkDestroyShaderModule(device, shaderModule, MemorySegment.NULL);
    }
    
    public static VkResult createShaderModule(MemorySegment device, MemorySegment createInfo, MemorySegment shaderModule) {
        int result = VulkanFFM.vkCreateShaderModule(device, createInfo, MemorySegment.NULL, shaderModule);
        return VkResult.fromInt(result);
    }
    
    public static VkResult queuePresentKHR(MemorySegment queue, MemorySegment presentInfo) {
        int result = VulkanFFM.vkQueuePresentKHR(queue, presentInfo);
        return VkResult.fromInt(result);
    }
    
    public static VkResult getPhysicalDeviceSurfaceSupportKHR(MemorySegment physicalDevice, int queueFamilyIndex, MemorySegment surface, MemorySegment supported) {
        int result = VulkanFFM.vkGetPhysicalDeviceSurfaceSupportKHR(physicalDevice, queueFamilyIndex, surface, supported);
        return VkResult.fromInt(result);
    }

    public static void cmdSetViewport(MemorySegment commandBuffer, int firstViewport, int viewportCount, MemorySegment viewports) {
        VulkanFFM.vkCmdSetViewport(commandBuffer, firstViewport, viewportCount, viewports);
    }

    public static void cmdSetScissor(MemorySegment commandBuffer, int firstScissor, int scissorCount, MemorySegment scissors) {
        VulkanFFM.vkCmdSetScissor(commandBuffer, firstScissor, scissorCount, scissors);
    }
    
    public static void cmdExecuteCommands(MemorySegment commandBuffer, int commandBufferCount, MemorySegment commandBuffers) {
        VulkanFFM.vkCmdExecuteCommands(commandBuffer, commandBufferCount, commandBuffers);
    }
    
    public static VkResult queueWaitIdle(MemorySegment queue) {
        int result = VulkanFFM.vkQueueWaitIdle(queue);
        return VkResult.fromInt(result);
    }
    
    public static void getDeviceQueue(MemorySegment device, int queueFamilyIndex, int queueIndex, MemorySegment queue) {
        VulkanFFM.vkGetDeviceQueue(device, queueFamilyIndex, queueIndex, queue);
    }
    
    public static void getPhysicalDeviceFormatProperties(MemorySegment physicalDevice, int format, MemorySegment formatProperties) {
        VulkanFFM.vkGetPhysicalDeviceFormatProperties(physicalDevice, format, formatProperties);
    }
}