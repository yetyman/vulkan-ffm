package io.github.yetyman.vulkan.sample.simple;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.highlevel.BaseRenderer;
import io.github.yetyman.vulkan.highlevel.ShaderLoader;
import io.github.yetyman.vulkan.util.Logger;
import java.lang.foreign.*;

public class SimpleRenderer extends BaseRenderer {
    private VkPipeline pipeline;
    
    public SimpleRenderer(Arena arena, MemorySegment device, MemorySegment queue,
                          MemorySegment surface, int width, int height) {
        super(arena, device, queue, surface, width, height, 3); // 3 frames in flight
    }
    
    @Override
    protected VkRenderPass createRenderPassImpl() {
        return VkRenderPass.builder()
            .device(device)
            .colorAttachment(VkFormat.VK_FORMAT_B8G8R8A8_SRGB, 
                           VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_CLEAR, 
                           VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_STORE)
            .subpassDependency(~0, 0, 
                VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT, 
                VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                0, VkAccessFlagBits.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
            .build(arena);
    }
    
    @Override
    protected VkFramebuffer createFramebufferImpl(int imageIndex) {
        return VkFramebuffer.builder()
            .device(device)
            .renderPass(renderPass.handle())
            .attachment(new VkFramebufferAttachment(swapchainImageViews[imageIndex], VkFramebufferAttachment.AttachmentType.COLOR, 0, 0))
            .dimensions(width, height)
            .build(arena);
    }
    
    @Override
    protected void initializeResources(MemorySegment physicalDevice, int queueFamilyIndex) {
        createGraphicsPipeline();
        Logger.info("Simple renderer initialized");
    }
    
    private void createGraphicsPipeline() {
        byte[] vertShaderCode = ShaderLoader.load("/shaders/triangle.vert").compile();
        byte[] fragShaderCode = ShaderLoader.load("/shaders/triangle.frag").compile();
        
        pipeline = VkPipeline.builder()
            .device(device)
            .renderPass(renderPass.handle())
            .viewport(0, 0, width, height)
            .vertexShader(vertShaderCode)
            .fragmentShader(fragShaderCode)
            .triangleTopology()
            .dynamicViewport()
            .dynamicScissor()
            .build(arena);
    }
    
    @Override
    protected void recordCommandBuffer(MemorySegment commandBuffer, int imageIndex, Arena frameArena) {
        VkCommandBuffer.begin(commandBuffer).execute(frameArena);
        
        VkCommandBuffer.beginRenderPass(commandBuffer, renderPass.handle(), framebuffers[imageIndex].handle())
            .renderArea(0, 0, width, height)
            .clearColor(0.0f, 0.0f, 0.0f, 1.0f)
            .execute(frameArena);
        
        VulkanExtensions.cmdBindPipeline(commandBuffer, VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.handle());
        
        // Set dynamic viewport and scissor
        MemorySegment viewport = VkViewport.builder()
            .position(0, 0)
            .size(width, height)
            .depthRange(0.0f, 1.0f)
            .build(frameArena);
        VulkanExtensions.cmdSetViewport(commandBuffer, 0, 1, viewport);
        
        MemorySegment scissor = VkRect2D.builder()
            .offset(0, 0)
            .extent(width, height)
            .build(frameArena);
        VulkanExtensions.cmdSetScissor(commandBuffer, 0, 1, scissor);
        
        VulkanExtensions.cmdDraw(commandBuffer, 3, 1, 0, 0);
        VulkanExtensions.cmdEndRenderPass(commandBuffer);
        VulkanExtensions.endCommandBuffer(commandBuffer).check();
    }
    
    @Override
    protected void cleanupResources() {
        if (pipeline != null) {
            pipeline.close();
        }
    }
}