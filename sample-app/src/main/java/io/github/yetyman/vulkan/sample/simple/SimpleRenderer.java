package io.github.yetyman.vulkan.sample.simple;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.highlevel.BaseRenderer;
import io.github.yetyman.vulkan.highlevel.DrawCommand;
import io.github.yetyman.vulkan.shaders.PushConstant;
import io.github.yetyman.vulkan.shaders.ShaderInstance;
import io.github.yetyman.vulkan.shaders.ShaderLoader;
import io.github.yetyman.vulkan.util.Logger;
import java.lang.foreign.*;

public class SimpleRenderer extends BaseRenderer {

    private ShaderInstance vertShader;
    private ShaderInstance fragShader;
    private PushConstant<Float> time;
    private VkPipeline pipeline;

    private final long startTime = System.nanoTime();

    public SimpleRenderer(Arena arena, VkDevice device, MemorySegment queue,
                          MemorySegment surface, int width, int height) {
        super(arena, device, queue, surface, width, height, 3);
    }

    @Override
    protected VkRenderPass createRenderPassImpl() {
        return VkRenderPass.builder()
            .device(device)
            .colorAttachment(VkFormat.VK_FORMAT_B8G8R8A8_SRGB.value(),
                           VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_CLEAR.value(),
                           VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_STORE.value())
            .subpassDependency(~0, 0,
                VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT.value(),
                VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT.value(),
                0, VkAccessFlagBits.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT.value())
            .build(arena);
    }

    @Override
    protected VkFramebuffer createFramebufferImpl(int imageIndex) {
        return VkFramebuffer.builder()
            .device(device)
            .renderPass(renderPass.handle())
            .attachment(new VkFramebufferAttachment(
                swapchainImageViews[imageIndex],
                VkFramebufferAttachment.AttachmentType.COLOR, 0, 0))
            .dimensions(width, height)
            .build(arena);
    }

    @Override
    protected void initializeResources(int queueFamilyIndex) {
        vertShader = ShaderLoader.load("/shaders/triangle.vert", device);
        fragShader = ShaderLoader.load("/shaders/triangle.frag", device);

        time = vertShader.getPushConstant("time", Float.class);

        VkPipeline.Builder pipelineBuilder = VkPipeline.builder()
            .device(device)
            .vertexShader(vertShader)
            .fragmentShader(fragShader)
            .triangleTopology()
            .dynamicViewport()
            .dynamicScissor();

        if (useDynamicRendering) {
            pipelineBuilder.dynamicRendering(0, VkFormat.VK_FORMAT_B8G8R8A8_SRGB.value());
        } else {
            pipelineBuilder.renderPass(renderPass.handle());
        }

        pipeline = pipelineBuilder.build(arena);
        vertShader.pipelineLayout(pipeline.layout());

        Logger.info("Simple renderer initialized (dynamic rendering: " + useDynamicRendering + ")");
    }

    @Override
    protected void recordCommandBuffer(VkCommandBuffer commandBuffer, int imageIndex, Arena frameArena) {
        VkCommandBuffer.begin(commandBuffer).execute(frameArena);

        if (useDynamicRendering) {
            // Transition swapchain image: UNDEFINED/PRESENT_SRC -> COLOR_ATTACHMENT_OPTIMAL
            VkImageBarrier.builder()
                .image(swapchainImageViews[imageIndex].image())
                .srcAccess(0)
                .dstAccess(VkAccessFlagBits.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT.value())
                .transition(
                    VkImageLayout.VK_IMAGE_LAYOUT_UNDEFINED.value(),
                    VkImageLayout.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL.value())
                .build(frameArena)
                .execute(commandBuffer.handle(),
                    VkPipelineStageFlagBits.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT.value(),
                    VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT.value());

            VkRendering.builder()
                .renderArea(0, 0, width, height)
                .colorAttachment(
                    swapchainImageViews[imageIndex].handle(),
                    VkImageLayout.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL.value(),
                    VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_CLEAR.value(),
                    VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_STORE.value(),
                    0.0f, 0.0f, 0.0f, 1.0f)
                .begin(commandBuffer.handle(), frameArena);
        } else {
            VkCommandBuffer.beginRenderPass(commandBuffer, renderPass.handle(), framebuffers[imageIndex].handle())
                .renderArea(0, 0, width, height)
                .clearColor(0.0f, 0.0f, 0.0f, 1.0f)
                .execute(frameArena);
        }

        Vulkan.cmdBindPipeline(commandBuffer.handle(),
            VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS.value(), pipeline.handle());

        MemorySegment viewport = VkViewport.builder()
            .position(0, 0).size(width, height).depthRange(0.0f, 1.0f).build(frameArena);
        Vulkan.cmdSetViewport(commandBuffer.handle(), 0, 1, viewport);

        MemorySegment scissor = VkRect2D.builder()
            .offset(0, 0).extent(width, height).build(frameArena);
        Vulkan.cmdSetScissor(commandBuffer.handle(), 0, 1, scissor);

        time.set((System.nanoTime() - startTime) / 1_000_000_000.0f);
        vertShader.flush(commandBuffer);

        DrawCommand.direct(3, 1000).execute(commandBuffer.handle());

        if (useDynamicRendering) {
            VkRendering.end(commandBuffer.handle());

            // Transition swapchain image: COLOR_ATTACHMENT_OPTIMAL -> PRESENT_SRC_KHR
            VkImageBarrier.builder()
                .image(swapchainImageViews[imageIndex].image())
                .srcAccess(VkAccessFlagBits.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT.value())
                .dstAccess(0)
                .transition(
                    VkImageLayout.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL.value(),
                    VkImageLayout.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR.value())
                .build(frameArena)
                .execute(commandBuffer.handle(),
                    VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT.value(),
                    VkPipelineStageFlagBits.VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT.value());
        } else {
            Vulkan.cmdEndRenderPass(commandBuffer.handle());
        }

        Vulkan.endCommandBuffer(commandBuffer.handle()).check();
    }

    @Override
    protected void cleanupResources() {
        if (pipeline != null) pipeline.close();
        if (vertShader != null) vertShader.close();
        if (fragShader != null) fragShader.close();
    }
}
