package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.generated.VulkanFFM;
import java.lang.foreign.*;

public class VulkanExtensions {
    
    public static VkResult createSwapchainKHR(MemorySegment device, MemorySegment createInfo, MemorySegment swapchain) {
        int result = VulkanFFM.vkCreateSwapchainKHR(device, createInfo, MemorySegment.NULL, swapchain);
        return VkResult.fromInt(result);
    }
    
    public static void destroySwapchainKHR(MemorySegment device, MemorySegment swapchain) {
        VulkanFFM.vkDestroySwapchainKHR(device, swapchain, MemorySegment.NULL);
    }
    
    public static VkResult getSwapchainImagesKHR(MemorySegment device, MemorySegment swapchain, MemorySegment count, MemorySegment images) {
        int result = VulkanFFM.vkGetSwapchainImagesKHR(device, swapchain, count, images);
        return VkResult.fromInt(result);
    }
    
    public static VkResult acquireNextImageKHR(MemorySegment device, MemorySegment swapchain, long timeout, 
                                               MemorySegment semaphore, MemorySegment fence, MemorySegment imageIndex) {
        int result = VulkanFFM.vkAcquireNextImageKHR(device, swapchain, timeout, semaphore, fence, imageIndex);
        return VkResult.fromInt(result);
    }
    
    public static VkResult queuePresentKHR(MemorySegment queue, MemorySegment presentInfo) {
        int result = VulkanFFM.vkQueuePresentKHR(queue, presentInfo);
        return VkResult.fromInt(result);
    }
    
    public static VkResult createImageView(MemorySegment device, MemorySegment createInfo, MemorySegment imageView) {
        int result = VulkanFFM.vkCreateImageView(device, createInfo, MemorySegment.NULL, imageView);
        return VkResult.fromInt(result);
    }
    
    public static void destroyImageView(MemorySegment device, MemorySegment imageView) {
        VulkanFFM.vkDestroyImageView(device, imageView, MemorySegment.NULL);
    }
    
    public static VkResult createRenderPass(MemorySegment device, MemorySegment createInfo, MemorySegment renderPass) {
        int result = VulkanFFM.vkCreateRenderPass(device, createInfo, MemorySegment.NULL, renderPass);
        return VkResult.fromInt(result);
    }
    
    public static void destroyRenderPass(MemorySegment device, MemorySegment renderPass) {
        VulkanFFM.vkDestroyRenderPass(device, renderPass, MemorySegment.NULL);
    }
    
    public static VkResult createFramebuffer(MemorySegment device, MemorySegment createInfo, MemorySegment framebuffer) {
        int result = VulkanFFM.vkCreateFramebuffer(device, createInfo, MemorySegment.NULL, framebuffer);
        return VkResult.fromInt(result);
    }
    
    public static void destroyFramebuffer(MemorySegment device, MemorySegment framebuffer) {
        VulkanFFM.vkDestroyFramebuffer(device, framebuffer, MemorySegment.NULL);
    }
    
    public static VkResult createShaderModule(MemorySegment device, MemorySegment createInfo, MemorySegment shaderModule) {
        int result = VulkanFFM.vkCreateShaderModule(device, createInfo, MemorySegment.NULL, shaderModule);
        return VkResult.fromInt(result);
    }
    
    public static void destroyShaderModule(MemorySegment device, MemorySegment shaderModule) {
        VulkanFFM.vkDestroyShaderModule(device, shaderModule, MemorySegment.NULL);
    }
    
    public static VkResult createPipelineLayout(MemorySegment device, MemorySegment createInfo, MemorySegment pipelineLayout) {
        int result = VulkanFFM.vkCreatePipelineLayout(device, createInfo, MemorySegment.NULL, pipelineLayout);
        return VkResult.fromInt(result);
    }
    
    public static void destroyPipelineLayout(MemorySegment device, MemorySegment pipelineLayout) {
        VulkanFFM.vkDestroyPipelineLayout(device, pipelineLayout, MemorySegment.NULL);
    }
    
    public static VkResult createGraphicsPipelines(MemorySegment device, MemorySegment pipelineCache, int count, 
                                                   MemorySegment createInfos, MemorySegment pipelines) {
        int result = VulkanFFM.vkCreateGraphicsPipelines(device, pipelineCache, count, createInfos, MemorySegment.NULL, pipelines);
        return VkResult.fromInt(result);
    }
    
    public static void destroyPipeline(MemorySegment device, MemorySegment pipeline) {
        VulkanFFM.vkDestroyPipeline(device, pipeline, MemorySegment.NULL);
    }
    
    public static VkResult createCommandPool(MemorySegment device, MemorySegment createInfo, MemorySegment commandPool) {
        int result = VulkanFFM.vkCreateCommandPool(device, createInfo, MemorySegment.NULL, commandPool);
        return VkResult.fromInt(result);
    }
    
    public static void destroyCommandPool(MemorySegment device, MemorySegment commandPool) {
        VulkanFFM.vkDestroyCommandPool(device, commandPool, MemorySegment.NULL);
    }
    
    public static VkResult allocateCommandBuffers(MemorySegment device, MemorySegment allocateInfo, MemorySegment commandBuffers) {
        int result = VulkanFFM.vkAllocateCommandBuffers(device, allocateInfo, commandBuffers);
        return VkResult.fromInt(result);
    }
    
    public static VkResult beginCommandBuffer(MemorySegment commandBuffer, MemorySegment beginInfo) {
        int result = VulkanFFM.vkBeginCommandBuffer(commandBuffer, beginInfo);
        return VkResult.fromInt(result);
    }
    
    public static VkResult endCommandBuffer(MemorySegment commandBuffer) {
        int result = VulkanFFM.vkEndCommandBuffer(commandBuffer);
        return VkResult.fromInt(result);
    }
    
    public static void cmdBeginRenderPass(MemorySegment commandBuffer, MemorySegment renderPassBegin, int contents) {
        VulkanFFM.vkCmdBeginRenderPass(commandBuffer, renderPassBegin, contents);
    }
    
    public static void cmdEndRenderPass(MemorySegment commandBuffer) {
        VulkanFFM.vkCmdEndRenderPass(commandBuffer);
    }
    
    public static void cmdBindPipeline(MemorySegment commandBuffer, int pipelineBindPoint, MemorySegment pipeline) {
        VulkanFFM.vkCmdBindPipeline(commandBuffer, pipelineBindPoint, pipeline);
    }
    
    public static void cmdDraw(MemorySegment commandBuffer, int vertexCount, int instanceCount, int firstVertex, int firstInstance) {
        VulkanFFM.vkCmdDraw(commandBuffer, vertexCount, instanceCount, firstVertex, firstInstance);
    }
    
    public static void cmdSetViewport(MemorySegment commandBuffer, int firstViewport, int viewportCount, MemorySegment viewports) {
        VulkanFFM.vkCmdSetViewport(commandBuffer, firstViewport, viewportCount, viewports);
    }
    
    public static void cmdSetScissor(MemorySegment commandBuffer, int firstScissor, int scissorCount, MemorySegment scissors) {
        VulkanFFM.vkCmdSetScissor(commandBuffer, firstScissor, scissorCount, scissors);
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
    
    public static VkResult queueSubmit(MemorySegment queue, int submitCount, MemorySegment submits, MemorySegment fence) {
        int result = VulkanFFM.vkQueueSubmit(queue, submitCount, submits, fence);
        return VkResult.fromInt(result);
    }
    
    public static void cmdExecuteCommands(MemorySegment commandBuffer, int commandBufferCount, MemorySegment commandBuffers) {
        VulkanFFM.vkCmdExecuteCommands(commandBuffer, commandBufferCount, commandBuffers);
    }
    
    public static VkResult createImage(MemorySegment device, MemorySegment createInfo, MemorySegment image) {
        int result = VulkanFFM.vkCreateImage(device, createInfo, MemorySegment.NULL, image);
        return VkResult.fromInt(result);
    }
    
    public static void getImageMemoryRequirements(MemorySegment device, MemorySegment image, MemorySegment memoryRequirements) {
        VulkanFFM.vkGetImageMemoryRequirements(device, image, memoryRequirements);
    }
    
    public static VkResult allocateMemory(MemorySegment device, MemorySegment allocateInfo, MemorySegment memory) {
        int result = VulkanFFM.vkAllocateMemory(device, allocateInfo, MemorySegment.NULL, memory);
        return VkResult.fromInt(result);
    }
    
    public static VkResult bindImageMemory(MemorySegment device, MemorySegment image, MemorySegment memory, long memoryOffset) {
        int result = VulkanFFM.vkBindImageMemory(device, image, memory, memoryOffset);
        return VkResult.fromInt(result);
    }
    
    public static void getPhysicalDeviceMemoryProperties(MemorySegment physicalDevice, MemorySegment memoryProperties) {
        VulkanFFM.vkGetPhysicalDeviceMemoryProperties(physicalDevice, memoryProperties);
    }
}