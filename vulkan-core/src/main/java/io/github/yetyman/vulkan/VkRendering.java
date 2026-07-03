package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;
import io.github.yetyman.vulkan.command.VkRenderPassCmd;
import io.github.yetyman.vulkan.util.BumpAllocator;

import java.lang.foreign.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Builder for dynamic rendering passes (Vulkan 1.3 / VK_KHR_dynamic_rendering).
 * Replaces VkRenderPass + VkFramebuffer for most use cases.
 *
 * <pre>{@code
 * VkRendering.builder()
 *     .device(device)
 *     .renderArea(0, 0, width, height)
 *     .colorAttachment(swapchainImageView, VkImageLayout.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
 *                      VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_CLEAR,
 *                      VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_STORE)
 *     .clearColor(0, 0, 0, 1)
 *     .depthAttachment(depthImageView, VkImageLayout.VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL,
 *                      VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_CLEAR,
 *                      VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_DONT_CARE)
 *     .begin(commandBuffer, arena);
 * // ... draw calls ...
 * VkRendering.end(device, commandBuffer);
 * }</pre>
 */
public class VkRendering {

    private VkRendering() {
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Ends a dynamic rendering pass.
     */
    public static void end(VkDevice device, MemorySegment commandBuffer) {
        try {
            VkRenderPassCmd.endRendering(device, commandBuffer);
        } catch (Throwable t) {
            throw new AssertionError("vkCmdEndRendering invokeExact signature mismatch", t);
        }
    }

    public static class Builder {
        private VkDevice device;
        private int x = 0, y = 0, width = 0, height = 0;
        private int layers = 1;
        private int viewMask = 0;
        private int flags = 0;
        private final List<AttachmentConfig> colorAttachments = new ArrayList<>(1);
        private AttachmentConfig depthAttachment = null;
        private AttachmentConfig stencilAttachment = null;

        private Builder() {
        }

        /**
         * Sets the device for dynamic rendering function loading.
         */
        public Builder device(VkDevice device) {
            this.device = device;
            return this;
        }

        /**
         * Sets the render area.
         */
        public Builder renderArea(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            return this;
        }

        /**
         * Adds a color attachment.
         *
         * @param imageView   the VkImageView handle
         * @param imageLayout the layout the image will be in during rendering
         * @param loadOp      what to do with existing contents at render pass start
         * @param storeOp     what to do with contents at render pass end
         */
        public Builder colorAttachment(MemorySegment imageView, int imageLayout, int loadOp, int storeOp) {
            colorAttachments.add(new AttachmentConfig(imageView, imageLayout, loadOp, storeOp,
                    MemorySegment.NULL, 0, 0, 0, 0, false));
            return this;
        }

        /**
         * Adds a color attachment with a clear value.
         *
         * @param imageView   the VkImageView handle
         * @param imageLayout the layout the image will be in during rendering
         * @param loadOp      what to do with existing contents — use CLEAR to apply the clear value
         * @param storeOp     what to do with contents at render pass end
         * @param r           clear color red
         * @param g           clear color green
         * @param b           clear color blue
         * @param a           clear color alpha
         */
        public Builder colorAttachment(MemorySegment imageView, int imageLayout, int loadOp, int storeOp,
                                       float r, float g, float b, float a) {
            colorAttachments.add(new AttachmentConfig(imageView, imageLayout, loadOp, storeOp,
                    MemorySegment.NULL, r, g, b, a, false));
            return this;
        }

        /**
         * Adds a color attachment with a resolve target for MSAA.
         *
         * @param imageView          the MSAA VkImageView handle
         * @param imageLayout        the layout the MSAA image will be in during rendering
         * @param loadOp             load op for the MSAA image
         * @param storeOp            store op for the MSAA image (typically DONT_CARE — resolved result is what matters)
         * @param resolveImageView   the resolve target VkImageView handle
         * @param resolveImageLayout the layout the resolve image will be in
         */
        public Builder colorAttachmentMSAA(MemorySegment imageView, int imageLayout, int loadOp, int storeOp,
                                           MemorySegment resolveImageView, int resolveImageLayout,
                                           float r, float g, float b, float a) {
            colorAttachments.add(new AttachmentConfig(imageView, imageLayout, loadOp, storeOp,
                    resolveImageView, r, g, b, a, false, resolveImageLayout));
            return this;
        }

        /**
         * Sets the depth attachment.
         */
        public Builder depthAttachment(MemorySegment imageView, int imageLayout, int loadOp, int storeOp) {
            this.depthAttachment = new AttachmentConfig(imageView, imageLayout, loadOp, storeOp,
                    MemorySegment.NULL, 1.0f, 0, 0, 0, true);
            return this;
        }

        /**
         * Sets the depth attachment with a custom clear depth value.
         */
        public Builder depthAttachment(MemorySegment imageView, int imageLayout, int loadOp, int storeOp,
                                       float clearDepth) {
            this.depthAttachment = new AttachmentConfig(imageView, imageLayout, loadOp, storeOp,
                    MemorySegment.NULL, clearDepth, 0, 0, 0, true);
            return this;
        }

        /**
         * Sets the stencil attachment.
         */
        public Builder stencilAttachment(MemorySegment imageView, int imageLayout, int loadOp, int storeOp) {
            this.stencilAttachment = new AttachmentConfig(imageView, imageLayout, loadOp, storeOp,
                    MemorySegment.NULL, 0, 0, 0, 0, true);
            return this;
        }

        /**
         * Sets the layer count for layered rendering (default: 1).
         */
        public Builder layers(int layers) {
            this.layers = layers;
            return this;
        }

        /**
         * Sets the view mask for multiview rendering (default: 0 = disabled).
         */
        public Builder viewMask(int viewMask) {
            this.viewMask = viewMask;
            return this;
        }

        /**
         * Sets rendering flags.
         */
        public Builder flags(int flags) {
            this.flags = flags;
            return this;
        }

        // Cached struct state for zero-allocation repeat calls
        private MemorySegment cachedRenderingInfo;
        private MemorySegment cachedColorArray;
        private long cachedAttachmentStride;

        /**
         * Patches the image view for a color attachment at the given index in the cached struct.
         * Only valid after at least one call to {@link #begin} or {@link #buildAndCache}.
         * Zero allocation.
         */
        public void patchColorView(int index, MemorySegment imageView) {
            MemorySegment slot = cachedColorArray.asSlice(
                index * cachedAttachmentStride, VkRenderingAttachmentInfo.layout());
            VkRenderingAttachmentInfo.imageView(slot, imageView);
        }

        /**
         * Patches the depth attachment image view in the cached struct. Zero allocation.
         */
        public void patchDepthView(MemorySegment imageView) {
            if (cachedRenderingInfo == null) return;
            MemorySegment depthSlot = VkRenderingInfo.pDepthAttachment(cachedRenderingInfo);
            if (depthSlot != null && !depthSlot.equals(MemorySegment.NULL)) {
                VkRenderingAttachmentInfo.imageView(depthSlot, imageView);
            }
        }

        /**
         * Patches the render area in the cached struct (e.g. after resize). Zero allocation.
         */
        public void patchRenderArea(int x, int y, int width, int height) {
            this.x = x; this.y = y; this.width = width; this.height = height;
            if (cachedRenderingInfo != null) {
                MemorySegment renderArea = VkRenderingInfo.renderArea(cachedRenderingInfo);
                MemorySegment offset = io.github.yetyman.vulkan.generated.VkRect2D.offset(renderArea);
                MemorySegment extent = io.github.yetyman.vulkan.generated.VkRect2D.extent(renderArea);
                VkOffset2D.x(offset, x);
                VkOffset2D.y(offset, y);
                VkExtent2D.width(extent, width);
                VkExtent2D.height(extent, height);
            }
        }

        /**
         * Builds the VkRenderingInfo into the given arena and caches it for future
         * {@link #beginCached} calls. Call this once at init time with a long-lived arena.
         */
        public void buildAndCache(Arena arena) {
            cachedRenderingInfo = buildRenderingInfo(arena);
        }

        /**
         * Begins a rendering pass using the cached struct. Zero allocation.
         * Requires a prior call to {@link #buildAndCache}.
         * Use {@link #patchColorView} to update image views before calling this.
         */
        public void beginCached(MemorySegment commandBuffer) {
            if (cachedRenderingInfo == null) throw new IllegalStateException("buildAndCache not called");
            try {
                VkRenderPassCmd.beginRendering(device, commandBuffer, cachedRenderingInfo);
            } catch (Throwable t) {
                throw new AssertionError("vkCmdBeginRendering invokeExact signature mismatch", t);
            }
        }

        /**
         * Builds the VkRenderingInfo and calls vkCmdBeginRendering.
         */
        public void begin(MemorySegment commandBuffer, SegmentAllocator allocator) {
            if (device == null) throw new IllegalStateException("device not set");

            BumpAllocator ba = BumpAllocator.get();
            ba.push();
            MemorySegment renderingInfo;
            try {
                renderingInfo = buildRenderingInfoInto(ba);
            } catch (Throwable t) {
                // build failed before the Vulkan call -- pop here since the second
                // try/finally below (which owns the pop for the success path) never runs
                ba.pop();
                throw t;
            }

            // Vulkan call outside the catch scope so the JIT can inline through invokeExact
            try {
                VkRenderPassCmd.beginRendering(device, commandBuffer, renderingInfo);
            } catch (Throwable t) {
                throw new AssertionError("vkCmdBeginRendering invokeExact signature mismatch", t);
            } finally {
                ba.pop();
            }
        }

        private MemorySegment buildRenderingInfo(Arena arena) {
            return buildRenderingInfoInto(arena);
        }

        private MemorySegment buildRenderingInfoInto(SegmentAllocator allocator) {
            MemorySegment renderingInfo = VkRenderingInfo.allocate(allocator);
            VkRenderingInfo.sType(renderingInfo, VkStructureType.VK_STRUCTURE_TYPE_RENDERING_INFO.value());
            VkRenderingInfo.pNext(renderingInfo, MemorySegment.NULL);
            VkRenderingInfo.flags(renderingInfo, flags);
            VkRenderingInfo.layerCount(renderingInfo, layers);
            VkRenderingInfo.viewMask(renderingInfo, viewMask);

            MemorySegment renderArea = VkRenderingInfo.renderArea(renderingInfo);
            MemorySegment offset = io.github.yetyman.vulkan.generated.VkRect2D.offset(renderArea);
            MemorySegment extent = io.github.yetyman.vulkan.generated.VkRect2D.extent(renderArea);
            VkOffset2D.x(offset, x);
            VkOffset2D.y(offset, y);
            VkExtent2D.width(extent, width);
            VkExtent2D.height(extent, height);

            if (!colorAttachments.isEmpty()) {
                cachedAttachmentStride = VkRenderingAttachmentInfo.layout().byteSize();
                MemorySegment colorArray = allocator.allocate(
                        cachedAttachmentStride * colorAttachments.size(),
                        VkRenderingAttachmentInfo.layout().byteAlignment());
                for (int i = 0; i < colorAttachments.size(); i++) {
                    MemorySegment slot = colorArray.asSlice(
                            i * cachedAttachmentStride,
                            VkRenderingAttachmentInfo.layout());
                    fillAttachment(slot, colorAttachments.get(i));
                }
                VkRenderingInfo.colorAttachmentCount(renderingInfo, colorAttachments.size());
                VkRenderingInfo.pColorAttachments(renderingInfo, colorArray);
                cachedColorArray = colorArray;
            }

            if (depthAttachment != null) {
                MemorySegment depthSlot = VkRenderingAttachmentInfo.allocate(allocator);
                fillAttachment(depthSlot, depthAttachment);
                VkRenderingInfo.pDepthAttachment(renderingInfo, depthSlot);
            } else {
                VkRenderingInfo.pDepthAttachment(renderingInfo, MemorySegment.NULL);
            }

            if (stencilAttachment != null) {
                MemorySegment stencilSlot = VkRenderingAttachmentInfo.allocate(allocator);
                fillAttachment(stencilSlot, stencilAttachment);
                VkRenderingInfo.pStencilAttachment(renderingInfo, stencilSlot);
            } else {
                VkRenderingInfo.pStencilAttachment(renderingInfo, MemorySegment.NULL);
            }

            cachedRenderingInfo = renderingInfo;
            return renderingInfo;
        }

        private void fillAttachment(MemorySegment slot, AttachmentConfig cfg) {
            VkRenderingAttachmentInfo.sType(slot, VkStructureType.VK_STRUCTURE_TYPE_RENDERING_ATTACHMENT_INFO.value());
            VkRenderingAttachmentInfo.pNext(slot, MemorySegment.NULL);
            VkRenderingAttachmentInfo.imageView(slot, cfg.imageView);
            VkRenderingAttachmentInfo.imageLayout(slot, cfg.imageLayout);
            VkRenderingAttachmentInfo.loadOp(slot, cfg.loadOp);
            VkRenderingAttachmentInfo.storeOp(slot, cfg.storeOp);

            if (cfg.resolveImageView != null && !cfg.resolveImageView.equals(MemorySegment.NULL)) {
                VkRenderingAttachmentInfo.resolveMode(slot,
                        VkResolveModeFlagBits.VK_RESOLVE_MODE_AVERAGE_BIT.value());
                VkRenderingAttachmentInfo.resolveImageView(slot, cfg.resolveImageView);
                VkRenderingAttachmentInfo.resolveImageLayout(slot, cfg.resolveImageLayout);
            } else {
                VkRenderingAttachmentInfo.resolveMode(slot, 0); // VK_RESOLVE_MODE_NONE
                VkRenderingAttachmentInfo.resolveImageView(slot, MemorySegment.NULL);
                VkRenderingAttachmentInfo.resolveImageLayout(slot,
                        VkImageLayout.VK_IMAGE_LAYOUT_UNDEFINED.value());
            }

            // Clear value — 16 bytes: either 4 floats (color) or float+uint (depth/stencil)
            MemorySegment clearValue = VkRenderingAttachmentInfo.clearValue(slot);
            if (cfg.isDepthStencil) {
                clearValue.set(ValueLayout.JAVA_FLOAT, 0, cfg.r); // depth in r field
                clearValue.set(ValueLayout.JAVA_INT, 4, 0);       // stencil
            } else {
                clearValue.set(ValueLayout.JAVA_FLOAT, 0, cfg.r);
                clearValue.set(ValueLayout.JAVA_FLOAT, 4, cfg.g);
                clearValue.set(ValueLayout.JAVA_FLOAT, 8, cfg.b);
                clearValue.set(ValueLayout.JAVA_FLOAT, 12, cfg.a);
            }
        }

        private record AttachmentConfig(MemorySegment imageView, int imageLayout, int loadOp, int storeOp,
                                        MemorySegment resolveImageView, float r, float g, float b, float a,
                                        boolean isDepthStencil, int resolveImageLayout) {
            AttachmentConfig(MemorySegment imageView, int imageLayout, int loadOp, int storeOp,
                             MemorySegment resolveImageView, float r, float g, float b, float a,
                             boolean isDepthStencil) {
                this(imageView, imageLayout, loadOp, storeOp, resolveImageView, r, g, b, a, isDepthStencil,
                        VkImageLayout.VK_IMAGE_LAYOUT_UNDEFINED.value());
            }
        }
    }
}
