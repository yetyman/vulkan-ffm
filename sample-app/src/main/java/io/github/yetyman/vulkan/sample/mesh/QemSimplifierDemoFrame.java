package io.github.yetyman.vulkan.sample.mesh;

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
import io.github.yetyman.vulkan.enums.VkImageAspectFlagBits;
import io.github.yetyman.vulkan.enums.VkImageLayout;
import io.github.yetyman.vulkan.enums.VkPipelineStageFlagBits;
import io.github.yetyman.vulkan.ui.UIComposite;
import io.github.yetyman.vulkan.ui.UIFrameContext;
import io.github.yetyman.vulkan.highlevel.GraphicsFrame;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;

/**
 * GraphicsFrame driving a UIComposite for the QEM simplifier demo.
 * Renders with depth buffer for proper 3D mesh visualization.
 */
public class QemSimplifierDemoFrame extends GraphicsFrame {

    private final UIComposite composite;
    private long frameNumber = 0;

    public QemSimplifierDemoFrame(Arena arena, VkDevice device, VkQueue queue,
                                  MemorySegment surface, int width, int height, UIComposite composite) {
        super(arena, device, queue, surface, width, height, 2);
        this.composite = composite;
    }

    @Override
    protected void initializeResources(int queueFamilyIndex) {
        composite.initialize();
    }

    @Override
    protected void recordCommandBuffer(VkCommandBuffer commandBuffer, int imageIndex,
                                       SegmentAllocator frameAllocator) {
        VkCommandBuffer.begin(commandBuffer).execute(frameAllocator);

        // Transition color image
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

        // Transition depth image
        VkImageBarrier.builder()
            .image(depthImage.handle())
            .srcAccess(0)
            .dstAccess(VkAccessFlagBits.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT.value())
            .transition(
                VkImageLayout.VK_IMAGE_LAYOUT_UNDEFINED.value(),
                VkImageLayout.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL.value())
            .aspectMask(VkImageAspectFlagBits.VK_IMAGE_ASPECT_DEPTH_BIT.value())
            .build(frameAllocator)
            .execute(commandBuffer.handle(),
                VkPipelineStageFlagBits.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT.value(),
                VkPipelineStageFlagBits.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT.value());

        double deltaTime = 1.0 / 60.0;
        UIFrameContext frameCtx = new UIFrameContext(frameArena(), deltaTime, frameNumber++,
                composite.context());
        composite.update(frameCtx);

        // Pre-render: compute dispatches, transfers, barriers outside the render pass
        composite.preRender(commandBuffer, frameArena());

        VkRendering.builder()
            .device(device)
            .renderArea(0, 0, width, height)
            .colorAttachment(
                swapchainImageViews[imageIndex].handle(),
                VkImageLayout.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL.value(),
                VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_CLEAR.value(),
                VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_STORE.value(),
                0.02f, 0.02f, 0.04f, 1.0f)
            .depthAttachment(
                depthImageView.handle(),
                VkImageLayout.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL.value(),
                VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_CLEAR.value(),
                VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_DONT_CARE.value(),
                1.0f)
            .begin(commandBuffer.handle(), frameAllocator);

        VkSetState.setViewport(commandBuffer, 0, 0, 0, width, height, 0.0f, 1.0f);
        VkSetState.setScissor(commandBuffer, 0, 0, 0, width, height);

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
        // composite is closed by the app
    }
}
