package io.github.yetyman.vulkan.sample.ui;

import io.github.yetyman.vulkan.Vulkan;
import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkImageBarrier;
import io.github.yetyman.vulkan.VkQueue;
import io.github.yetyman.vulkan.VkRendering;
import io.github.yetyman.vulkan.command.VkSetState;
import io.github.yetyman.vulkan.enums.VkAccessFlagBits;
import io.github.yetyman.vulkan.enums.VkAttachmentLoadOp;
import io.github.yetyman.vulkan.enums.VkAttachmentStoreOp;
import io.github.yetyman.vulkan.enums.VkImageLayout;
import io.github.yetyman.vulkan.enums.VkPipelineStageFlagBits;
import io.github.yetyman.vulkan.foundation.ui.UIComposite;
import io.github.yetyman.vulkan.foundation.ui.UIFrameContext;
import io.github.yetyman.vulkan.highlevel.GraphicsFrame;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;

/**
 * Minimal GraphicsFrame driving a UIComposite (currently just GPUDrivenTextLayer) through the
 * direct-render path (UIComposite.render(), no render graph). Clears the swapchain image then
 * lets the composite's layers record their own draws.
 */
public class TextExampleFrame extends GraphicsFrame {

    private final UIComposite composite;
    private long frameNumber = 0;
    private final long startNanos = System.nanoTime();

    public TextExampleFrame(java.lang.foreign.Arena arena, VkDevice device, VkQueue queue,
                             MemorySegment surface, int width, int height, UIComposite composite) {
        super(arena, device, queue, surface, width, height, 2);
        this.composite = composite;
    }

    @Override
    protected void initializeResources(int queueFamilyIndex) {
        composite.initialize();
    }

    @Override
    protected void recordCommandBuffer(VkCommandBuffer commandBuffer, int imageIndex, SegmentAllocator frameAllocator) {
        VkCommandBuffer.begin(commandBuffer).execute(frameAllocator);

        VkImageBarrier.builder()
            .image(swapchainImageViews[imageIndex].image())
            .srcAccess(0)
            .dstAccess(VkAccessFlagBits.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT.value())
            .transition(
                VkImageLayout.VK_IMAGE_LAYOUT_UNDEFINED.value(),
                VkImageLayout.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL.value())
            .build(frameAllocator)
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
                0.05f, 0.05f, 0.08f, 1.0f)
            .begin(commandBuffer.handle(), frameAllocator);

        VkSetState.setViewport(commandBuffer, 0, 0, 0, width, height, 0.0f, 1.0f);
        VkSetState.setScissor(commandBuffer, 0, 0, 0, width, height);

        double deltaTime = 1.0 / 60.0;
        UIFrameContext frameCtx = new UIFrameContext(frameArena(), deltaTime, frameNumber++, composite.context());
        composite.update(frameCtx);
        composite.render(commandBuffer, frameArena());

        VkRendering.end(device, commandBuffer.handle());

        VkImageBarrier.builder()
            .image(swapchainImageViews[imageIndex].image())
            .srcAccess(VkAccessFlagBits.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT.value())
            .dstAccess(0)
            .transition(
                VkImageLayout.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL.value(),
                VkImageLayout.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR.value())
            .build(frameAllocator)
            .execute(commandBuffer.handle(),
                VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT.value(),
                VkPipelineStageFlagBits.VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT.value());

        Vulkan.endCommandBuffer(commandBuffer.handle()).check();
    }

    @Override
    protected void onResize(int width, int height) {
        composite.resize(width, height);
    }

    @Override
    protected void cleanupResources() {
        composite.close();
    }
}
