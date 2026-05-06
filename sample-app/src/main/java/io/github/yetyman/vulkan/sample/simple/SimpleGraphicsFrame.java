package io.github.yetyman.vulkan.sample.simple;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.command.VkBind;
import io.github.yetyman.vulkan.command.VkSetState;
import io.github.yetyman.vulkan.command.VkRenderPassCmd;
import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.highlevel.DrawCommand;
import io.github.yetyman.vulkan.highlevel.GraphicsFrame;

import java.lang.foreign.*;

/**
 * A {@link GraphicsFrame} subclass for the common case of a single graphics pipeline
 * with a fullscreen draw. Handles the standard per-frame command buffer structure:
 * begin, image barriers, render pass / dynamic rendering, viewport/scissor, pipeline bind,
 * draw, end. Subclasses provide the pipeline and per-frame logic.
 */
public abstract class SimpleGraphicsFrame extends GraphicsFrame {

    protected VkPipeline pipeline;

    protected SimpleGraphicsFrame(Arena arena, VkDevice device, VkQueue queue,
                                  MemorySegment surface, int width, int height, int maxFramesInFlight) {
        super(arena, device, queue, surface, width, height, maxFramesInFlight);
    }

    /**
     * @return the pipeline to use for rendering. Called once during {@link #initializeResources}.
     */
    protected abstract VkPipeline createPipeline();

    /**
     * Called each frame before the render pass begins. Use for barriers that must be outside
     * the render pass (e.g. compute-to-vertex memory barriers).
     */
    protected void beforeRenderPass(VkCommandBuffer commandBuffer, Arena frameArena) {
    }

    /**
     * Called each frame after pipeline bind and viewport/scissor setup, before the draw call.
     */
    protected abstract void onDraw(VkCommandBuffer commandBuffer, Arena frameArena);

    /**
     * @return the number of vertices to draw each frame.
     */
    protected abstract int vertexCount();

    /**
     * @return the number of instances to draw each frame. Defaults to 1.
     */
    protected int instanceCount() {
        return 1;
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
        pipeline = createPipeline();
    }

    @Override
    protected void recordCommandBuffer(VkCommandBuffer commandBuffer, int imageIndex, Arena frameArena) {
        VkCommandBuffer.begin(commandBuffer).execute(frameArena);

        beforeRenderPass(commandBuffer, frameArena);

        if (useDynamicRendering) {
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
                    .device(device)
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

        VkBind.bindPipeline(commandBuffer, VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS.value(), pipeline.handle());

        VkSetState.setViewport(commandBuffer, 0, 0, 0, width, height, 0.0f, 1.0f);
        VkSetState.setScissor(commandBuffer, 0, 0, 0, width, height);

        onDraw(commandBuffer, frameArena);

        DrawCommand.direct(vertexCount(), instanceCount()).execute(commandBuffer.handle());

        if (useDynamicRendering) {
            VkRendering.end(device, commandBuffer.handle());

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
            VkRenderPassCmd.endRenderPass(commandBuffer);
        }

        Vulkan.endCommandBuffer(commandBuffer.handle()).check();
    }

    @Override
    protected void cleanupResources() {
        if (pipeline != null) pipeline.close();
    }
}
