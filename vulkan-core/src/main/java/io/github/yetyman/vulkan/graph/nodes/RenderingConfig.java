package io.github.yetyman.vulkan.graph.nodes;

import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkRendering;
import io.github.yetyman.vulkan.enums.VkAttachmentLoadOp;
import io.github.yetyman.vulkan.enums.VkAttachmentStoreOp;
import io.github.yetyman.vulkan.enums.VkImageLayout;
import io.github.yetyman.vulkan.graph.edges.ResourceEdge;
import io.github.yetyman.vulkan.graph.resources.GraphImageResource;
import io.github.yetyman.vulkan.graph.resources.GraphResource;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

/**
 * Pre-built dynamic rendering configuration for a GraphicsPassNode.
 * When attached to a node, the executor automatically begins dynamic rendering
 * before calling execute() and ends it after, using a cached VkRenderingInfo struct
 * that requires zero per-frame allocation.
 *
 * The rendering config is derived from the node's write edges: any write to a
 * GraphImageResource with COLOR_ATTACHMENT_WRITE access becomes a color attachment,
 * and DEPTH_STENCIL_ATTACHMENT_WRITE becomes the depth attachment.
 *
 * Image views are patched each frame (zero-copy pointer write into the cached struct)
 * to handle swapchain image rotation or resource re-creation after resize.
 */
public class RenderingConfig {

    private static final int VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT = 0x00000100;
    private static final int VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT = 0x00000400;

    private final VkRendering.Builder renderingBuilder;
    private final List<ColorAttachmentSlot> colorSlots;
    private final DepthAttachmentSlot depthSlot;
    private boolean built = false;

    private RenderingConfig(VkRendering.Builder builder, List<ColorAttachmentSlot> colorSlots,
                            DepthAttachmentSlot depthSlot) {
        this.renderingBuilder = builder;
        this.colorSlots = colorSlots;
        this.depthSlot = depthSlot;
    }

    /**
     * Builds the rendering config from a GraphicsPassNode's write edges.
     * Call once at graph compile time.
     *
     * @param device the logical device
     * @param writes the node's write edges
     * @param renderWidth render area width
     * @param renderHeight render area height
     * @param colorLoadOp load op for all color attachments
     * @param colorStoreOp store op for all color attachments
     * @param depthLoadOp load op for depth attachment
     * @param depthStoreOp store op for depth attachment
     * @param clearR clear color red
     * @param clearG clear color green
     * @param clearB clear color blue
     * @param clearA clear color alpha
     * @param clearDepth clear depth value
     */
    public static RenderingConfig fromWriteEdges(VkDevice device, List<ResourceEdge> writes,
                                                  int renderWidth, int renderHeight,
                                                  int colorLoadOp, int colorStoreOp,
                                                  int depthLoadOp, int depthStoreOp,
                                                  float clearR, float clearG, float clearB, float clearA,
                                                  float clearDepth) {
        VkRendering.Builder builder = VkRendering.builder().device(device)
            .renderArea(0, 0, renderWidth, renderHeight);

        List<ColorAttachmentSlot> colorSlots = new ArrayList<>();
        DepthAttachmentSlot depthSlot = null;

        for (ResourceEdge edge : writes) {
            GraphResource res = edge.resource();
            if (!(res instanceof GraphImageResource imgRes)) continue;

            int access = edge.accessMask();
            int layout = edge.imageLayout() >= 0 ? edge.imageLayout()
                : VkImageLayout.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL.value();

            if ((access & VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT) != 0) {
                MemorySegment view = imgRes.imageView();
                if (view.equals(MemorySegment.NULL)) view = imgRes.handle(); // fallback
                builder.colorAttachment(view, layout, colorLoadOp, colorStoreOp,
                    clearR, clearG, clearB, clearA);
                colorSlots.add(new ColorAttachmentSlot(imgRes, colorSlots.size()));
            } else if ((access & VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT) != 0) {
                int depthLayout = edge.imageLayout() >= 0 ? edge.imageLayout()
                    : VkImageLayout.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL.value();
                MemorySegment view = imgRes.imageView();
                if (view.equals(MemorySegment.NULL)) view = imgRes.handle(); // fallback
                builder.depthAttachment(view, depthLayout, depthLoadOp, depthStoreOp, clearDepth);
                depthSlot = new DepthAttachmentSlot(imgRes);
            }
        }

        return new RenderingConfig(builder, colorSlots, depthSlot);
    }

    /**
     * Allocates the VkRenderingInfo struct into a long-lived arena. Call once.
     */
    public void buildAndCache(Arena arena) {
        renderingBuilder.buildAndCache(arena);
        built = true;
    }

    /**
     * Patches image views and begins dynamic rendering. Zero allocation.
     */
    public void beginRendering(MemorySegment commandBuffer) {
        if (!built) throw new IllegalStateException("buildAndCache not called");

        // Patch color attachment image views (handles may change per-frame for swapchain images)
        for (int i = 0; i < colorSlots.size(); i++) {
            ColorAttachmentSlot slot = colorSlots.get(i);
            MemorySegment view = slot.resource.imageView();
            if (view.equals(MemorySegment.NULL)) view = slot.resource.handle();
            renderingBuilder.patchColorView(i, view);
        }

        // Patch depth view
        if (depthSlot != null) {
            MemorySegment view = depthSlot.resource.imageView();
            if (view.equals(MemorySegment.NULL)) view = depthSlot.resource.handle();
            renderingBuilder.patchDepthView(view);
        }

        renderingBuilder.beginCached(commandBuffer);
    }

    /**
     * Ends dynamic rendering.
     */
    public void endRendering(VkDevice device, MemorySegment commandBuffer) {
        VkRendering.end(device, commandBuffer);
    }

    /**
     * Updates the render area (e.g. after resize). Zero allocation if already built.
     */
    public void resize(int width, int height) {
        renderingBuilder.patchRenderArea(0, 0, width, height);
    }

    /** @return true if this config has been built and cached */
    public boolean isBuilt() { return built; }

    /** @return the underlying VkRendering.Builder for advanced patching */
    public VkRendering.Builder renderingBuilder() { return renderingBuilder; }

    private record ColorAttachmentSlot(GraphImageResource resource, int index) {}
    private record DepthAttachmentSlot(GraphImageResource resource) {}
}
