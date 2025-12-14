package io.github.yetyman.vulkan;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

public class VulkanExtensions {
    private static final MethodHandle vkCreateSwapchainKHR;
    private static final MethodHandle vkDestroySwapchainKHR;
    private static final MethodHandle vkGetSwapchainImagesKHR;
    private static final MethodHandle vkAcquireNextImageKHR;
    private static final MethodHandle vkQueuePresentKHR;
    private static final MethodHandle vkCreateImageView;
    private static final MethodHandle vkDestroyImageView;
    private static final MethodHandle vkCreateRenderPass;
    private static final MethodHandle vkDestroyRenderPass;
    private static final MethodHandle vkCreateFramebuffer;
    private static final MethodHandle vkDestroyFramebuffer;
    private static final MethodHandle vkCreateShaderModule;
    private static final MethodHandle vkDestroyShaderModule;
    private static final MethodHandle vkCreatePipelineLayout;
    private static final MethodHandle vkDestroyPipelineLayout;
    private static final MethodHandle vkCreateGraphicsPipelines;
    private static final MethodHandle vkDestroyPipeline;
    private static final MethodHandle vkCreateCommandPool;
    private static final MethodHandle vkDestroyCommandPool;
    private static final MethodHandle vkAllocateCommandBuffers;
    private static final MethodHandle vkBeginCommandBuffer;
    private static final MethodHandle vkEndCommandBuffer;
    private static final MethodHandle vkCmdBeginRenderPass;
    private static final MethodHandle vkCmdEndRenderPass;
    private static final MethodHandle vkCmdBindPipeline;
    private static final MethodHandle vkCmdDraw;
    private static final MethodHandle vkCmdSetViewport;
    private static final MethodHandle vkCmdSetScissor;
    private static final MethodHandle vkCreateSemaphore;
    private static final MethodHandle vkDestroySemaphore;
    private static final MethodHandle vkCreateFence;
    private static final MethodHandle vkDestroyFence;
    private static final MethodHandle vkWaitForFences;
    private static final MethodHandle vkResetFences;
    private static final MethodHandle vkQueueSubmit;
    
    static {
        vkCreateSwapchainKHR = VulkanLibrary.findFunction("vkCreateSwapchainKHR",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        vkDestroySwapchainKHR = VulkanLibrary.findFunction("vkDestroySwapchainKHR",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        vkGetSwapchainImagesKHR = VulkanLibrary.findFunction("vkGetSwapchainImagesKHR",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        vkAcquireNextImageKHR = VulkanLibrary.findFunction("vkAcquireNextImageKHR",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        vkQueuePresentKHR = VulkanLibrary.findFunction("vkQueuePresentKHR",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        vkCreateImageView = VulkanLibrary.findFunction("vkCreateImageView",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        vkDestroyImageView = VulkanLibrary.findFunction("vkDestroyImageView",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        vkCreateRenderPass = VulkanLibrary.findFunction("vkCreateRenderPass",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        vkDestroyRenderPass = VulkanLibrary.findFunction("vkDestroyRenderPass",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        vkCreateFramebuffer = VulkanLibrary.findFunction("vkCreateFramebuffer",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        vkDestroyFramebuffer = VulkanLibrary.findFunction("vkDestroyFramebuffer",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        vkCreateShaderModule = VulkanLibrary.findFunction("vkCreateShaderModule",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        vkDestroyShaderModule = VulkanLibrary.findFunction("vkDestroyShaderModule",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        vkCreatePipelineLayout = VulkanLibrary.findFunction("vkCreatePipelineLayout",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        vkDestroyPipelineLayout = VulkanLibrary.findFunction("vkDestroyPipelineLayout",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        vkCreateGraphicsPipelines = VulkanLibrary.findFunction("vkCreateGraphicsPipelines",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        vkDestroyPipeline = VulkanLibrary.findFunction("vkDestroyPipeline",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        vkCreateCommandPool = VulkanLibrary.findFunction("vkCreateCommandPool",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        vkDestroyCommandPool = VulkanLibrary.findFunction("vkDestroyCommandPool",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        vkAllocateCommandBuffers = VulkanLibrary.findFunction("vkAllocateCommandBuffers",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        vkBeginCommandBuffer = VulkanLibrary.findFunction("vkBeginCommandBuffer",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        vkEndCommandBuffer = VulkanLibrary.findFunction("vkEndCommandBuffer",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        vkCmdBeginRenderPass = VulkanLibrary.findFunction("vkCmdBeginRenderPass",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        vkCmdEndRenderPass = VulkanLibrary.findFunction("vkCmdEndRenderPass",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        vkCmdBindPipeline = VulkanLibrary.findFunction("vkCmdBindPipeline",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        vkCmdDraw = VulkanLibrary.findFunction("vkCmdDraw",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        vkCmdSetViewport = VulkanLibrary.findFunction("vkCmdSetViewport",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        vkCmdSetScissor = VulkanLibrary.findFunction("vkCmdSetScissor",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        vkCreateSemaphore = VulkanLibrary.findFunction("vkCreateSemaphore",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        vkDestroySemaphore = VulkanLibrary.findFunction("vkDestroySemaphore",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        vkCreateFence = VulkanLibrary.findFunction("vkCreateFence",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        vkDestroyFence = VulkanLibrary.findFunction("vkDestroyFence",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        vkWaitForFences = VulkanLibrary.findFunction("vkWaitForFences",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
        vkResetFences = VulkanLibrary.findFunction("vkResetFences",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        vkQueueSubmit = VulkanLibrary.findFunction("vkQueueSubmit",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    }
    
    public static VkResult createSwapchainKHR(MemorySegment device, MemorySegment createInfo, MemorySegment swapchain) {
        try {
            int result = (int) vkCreateSwapchainKHR.invoke(device, createInfo, MemorySegment.NULL, swapchain);
            return VkResult.fromInt(result);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
    
    public static void destroySwapchainKHR(MemorySegment device, MemorySegment swapchain) {
        try {
            vkDestroySwapchainKHR.invoke(device, swapchain, MemorySegment.NULL);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
    
    public static VkResult getSwapchainImagesKHR(MemorySegment device, MemorySegment swapchain, MemorySegment count, MemorySegment images) {
        try {
            int result = (int) vkGetSwapchainImagesKHR.invoke(device, swapchain, count, images);
            return VkResult.fromInt(result);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
    
    public static VkResult acquireNextImageKHR(MemorySegment device, MemorySegment swapchain, long timeout, 
                                               MemorySegment semaphore, MemorySegment fence, MemorySegment imageIndex) {
        try {
            int result = (int) vkAcquireNextImageKHR.invoke(device, swapchain, timeout, semaphore, fence, imageIndex);
            return VkResult.fromInt(result);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
    
    public static VkResult queuePresentKHR(MemorySegment queue, MemorySegment presentInfo) {
        try {
            int result = (int) vkQueuePresentKHR.invoke(queue, presentInfo);
            return VkResult.fromInt(result);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
    
    public static VkResult createImageView(MemorySegment device, MemorySegment createInfo, MemorySegment imageView) {
        try {
            int result = (int) vkCreateImageView.invoke(device, createInfo, MemorySegment.NULL, imageView);
            return VkResult.fromInt(result);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
    
    public static void destroyImageView(MemorySegment device, MemorySegment imageView) {
        try {
            vkDestroyImageView.invoke(device, imageView, MemorySegment.NULL);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
    
    public static VkResult createRenderPass(MemorySegment device, MemorySegment createInfo, MemorySegment renderPass) {
        try {
            int result = (int) vkCreateRenderPass.invoke(device, createInfo, MemorySegment.NULL, renderPass);
            return VkResult.fromInt(result);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
    
    public static void destroyRenderPass(MemorySegment device, MemorySegment renderPass) {
        try {
            vkDestroyRenderPass.invoke(device, renderPass, MemorySegment.NULL);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
    
    public static VkResult createFramebuffer(MemorySegment device, MemorySegment createInfo, MemorySegment framebuffer) {
        try {
            int result = (int) vkCreateFramebuffer.invoke(device, createInfo, MemorySegment.NULL, framebuffer);
            return VkResult.fromInt(result);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
    
    public static void destroyFramebuffer(MemorySegment device, MemorySegment framebuffer) {
        try {
            vkDestroyFramebuffer.invoke(device, framebuffer, MemorySegment.NULL);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
    
    public static VkResult createShaderModule(MemorySegment device, MemorySegment createInfo, MemorySegment shaderModule) {
        try {
            int result = (int) vkCreateShaderModule.invoke(device, createInfo, MemorySegment.NULL, shaderModule);
            return VkResult.fromInt(result);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
    
    public static void destroyShaderModule(MemorySegment device, MemorySegment shaderModule) {
        try {
            vkDestroyShaderModule.invoke(device, shaderModule, MemorySegment.NULL);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
    
    public static VkResult createPipelineLayout(MemorySegment device, MemorySegment createInfo, MemorySegment pipelineLayout) {
        try {
            int result = (int) vkCreatePipelineLayout.invoke(device, createInfo, MemorySegment.NULL, pipelineLayout);
            return VkResult.fromInt(result);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
    
    public static void destroyPipelineLayout(MemorySegment device, MemorySegment pipelineLayout) {
        try {
            vkDestroyPipelineLayout.invoke(device, pipelineLayout, MemorySegment.NULL);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
    
    public static VkResult createGraphicsPipelines(MemorySegment device, MemorySegment pipelineCache, int count, 
                                                   MemorySegment createInfos, MemorySegment pipelines) {
        try {
            int result = (int) vkCreateGraphicsPipelines.invoke(device, pipelineCache, count, createInfos, MemorySegment.NULL, pipelines);
            return VkResult.fromInt(result);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
    
    public static void destroyPipeline(MemorySegment device, MemorySegment pipeline) {
        try {
            vkDestroyPipeline.invoke(device, pipeline, MemorySegment.NULL);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
    
    public static VkResult createCommandPool(MemorySegment device, MemorySegment createInfo, MemorySegment commandPool) {
        try {
            int result = (int) vkCreateCommandPool.invoke(device, createInfo, MemorySegment.NULL, commandPool);
            return VkResult.fromInt(result);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
    
    public static void destroyCommandPool(MemorySegment device, MemorySegment commandPool) {
        try {
            vkDestroyCommandPool.invoke(device, commandPool, MemorySegment.NULL);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
    
    public static VkResult allocateCommandBuffers(MemorySegment device, MemorySegment allocateInfo, MemorySegment commandBuffers) {
        try {
            int result = (int) vkAllocateCommandBuffers.invoke(device, allocateInfo, commandBuffers);
            return VkResult.fromInt(result);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
    
    public static VkResult beginCommandBuffer(MemorySegment commandBuffer, MemorySegment beginInfo) {
        try {
            int result = (int) vkBeginCommandBuffer.invoke(commandBuffer, beginInfo);
            return VkResult.fromInt(result);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
    
    public static VkResult endCommandBuffer(MemorySegment commandBuffer) {
        try {
            int result = (int) vkEndCommandBuffer.invoke(commandBuffer);
            return VkResult.fromInt(result);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
    
    public static void cmdBeginRenderPass(MemorySegment commandBuffer, MemorySegment renderPassBegin, int contents) {
        try {
            vkCmdBeginRenderPass.invoke(commandBuffer, renderPassBegin, contents);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
    
    public static void cmdEndRenderPass(MemorySegment commandBuffer) {
        try {
            vkCmdEndRenderPass.invoke(commandBuffer);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
    
    public static void cmdBindPipeline(MemorySegment commandBuffer, int pipelineBindPoint, MemorySegment pipeline) {
        try {
            vkCmdBindPipeline.invoke(commandBuffer, pipelineBindPoint, pipeline);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
    
    public static void cmdDraw(MemorySegment commandBuffer, int vertexCount, int instanceCount, int firstVertex, int firstInstance) {
        try {
            vkCmdDraw.invoke(commandBuffer, vertexCount, instanceCount, firstVertex, firstInstance);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
    
    public static void cmdSetViewport(MemorySegment commandBuffer, int firstViewport, int viewportCount, MemorySegment viewports) {
        try {
            vkCmdSetViewport.invoke(commandBuffer, firstViewport, viewportCount, viewports);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
    
    public static void cmdSetScissor(MemorySegment commandBuffer, int firstScissor, int scissorCount, MemorySegment scissors) {
        try {
            vkCmdSetScissor.invoke(commandBuffer, firstScissor, scissorCount, scissors);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
    
    public static VkResult createSemaphore(MemorySegment device, MemorySegment createInfo, MemorySegment semaphore) {
        try {
            int result = (int) vkCreateSemaphore.invoke(device, createInfo, MemorySegment.NULL, semaphore);
            return VkResult.fromInt(result);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
    
    public static void destroySemaphore(MemorySegment device, MemorySegment semaphore) {
        try {
            vkDestroySemaphore.invoke(device, semaphore, MemorySegment.NULL);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
    
    public static VkResult createFence(MemorySegment device, MemorySegment createInfo, MemorySegment fence) {
        try {
            int result = (int) vkCreateFence.invoke(device, createInfo, MemorySegment.NULL, fence);
            return VkResult.fromInt(result);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
    
    public static void destroyFence(MemorySegment device, MemorySegment fence) {
        try {
            vkDestroyFence.invoke(device, fence, MemorySegment.NULL);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
    
    public static VkResult waitForFences(MemorySegment device, int fenceCount, MemorySegment fences, int waitAll, long timeout) {
        try {
            int result = (int) vkWaitForFences.invoke(device, fenceCount, fences, waitAll, timeout);
            return VkResult.fromInt(result);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
    
    public static VkResult resetFences(MemorySegment device, int fenceCount, MemorySegment fences) {
        try {
            int result = (int) vkResetFences.invoke(device, fenceCount, fences);
            return VkResult.fromInt(result);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
    
    public static VkResult queueSubmit(MemorySegment queue, int submitCount, MemorySegment submits, MemorySegment fence) {
        try {
            int result = (int) vkQueueSubmit.invoke(queue, submitCount, submits, fence);
            return VkResult.fromInt(result);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
