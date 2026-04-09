package io.github.yetyman.vulkan.sample.simple;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.highlevel.DrawCommand;
import io.github.yetyman.vulkan.highlevel.GraphicsRenderer;

import java.lang.foreign.*;

/**
 * Abstract renderer that owns a single graphics pipeline and handles the standard
 * per-frame command buffer structure: begin, viewport/scissor, pipeline bind, draw, end.
 *
 * Subclasses provide the pipeline and per-frame logic via {@link #createPipeline()}
 * and {@link #onFrame(VkCommandBuffer, Arena)}.
 */
public abstract class SimplePipelineLoop extends GraphicsRenderer {

    protected VkPipeline pipeline;

    protected SimplePipelineLoop(Arena arena, VkDevice device, MemorySegment queue,
                                  MemorySegment surface, int width, int height, int maxFramesInFlight) {
        super(arena, device, queue, surface, width, height, maxFramesInFlight);
    }

    /** @return the pipeline to use for rendering. Called once during {@link #initializeResources}. */
    protected abstract VkPipeline createPipeline();

    /** Called each frame after pipeline bind and viewport/scissor setup, before the draw call. */
    protected abstract void onFrame(VkCommandBuffer commandBuffer, Arena frameArena);

    /** @return the number of vertices to draw each frame. */
    protected abstract int vertexCount();

    /** @return the number of instances to draw each frame. Defaults to 1. */
    protected int instanceCount() { return 1; }

    @Override
    protected void initializeResources(int queueFamilyIndex) {
        pipeline = createPipeline();
    }

    @Override
    protected void recordCommandBuffer(VkCommandBuffer commandBuffer, int imageIndex, Arena frameArena) {
        VkCommandBuffer.begin(commandBuffer).execute(frameArena);

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

        onFrame(commandBuffer, frameArena);

        DrawCommand.direct(vertexCount(), instanceCount()).execute(commandBuffer.handle());

        if (useDynamicRendering) {
            VkRendering.end(commandBuffer.handle());

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
    }
}
