package io.github.yetyman.vulkan.command;

import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;

import java.lang.foreign.*;

/**
 * Vulkan image blit command wrapper for scaling and filtering image copies.
 */
public record VkBlit(MemorySegment srcImage, int srcLayout, MemorySegment dstImage, int dstLayout,
                     int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1,
                     int filter) {

    // Static helpers (zero allocation)
    public static void blitImage(VkCommandBuffer cmd, MemorySegment srcImage, int srcLayout, MemorySegment dstImage, int dstLayout,
                                 int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int filter) {
        blitImage(cmd.handle(), srcImage, srcLayout, dstImage, dstLayout, srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, filter);
    }

    public static void blitImage(MemorySegment cmd, MemorySegment srcImage, int srcLayout, MemorySegment dstImage, int dstLayout,
                                 int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int filter) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment region = VkImageBlit.allocate(arena);

            MemorySegment srcSubresource = VkImageBlit.srcSubresource(region);
            VkImageSubresourceLayers.aspectMask(srcSubresource, VkImageAspectFlagBits.VK_IMAGE_ASPECT_COLOR_BIT.value());
            VkImageSubresourceLayers.mipLevel(srcSubresource, 0);
            VkImageSubresourceLayers.baseArrayLayer(srcSubresource, 0);
            VkImageSubresourceLayers.layerCount(srcSubresource, 1);

            MemorySegment srcOffsets = VkImageBlit.srcOffsets(region);
            MemorySegment srcOffset0 = srcOffsets.asSlice(0, VkOffset3D.sizeof());
            MemorySegment srcOffset1 = srcOffsets.asSlice(VkOffset3D.sizeof(), VkOffset3D.sizeof());
            VkOffset3D.x(srcOffset0, srcX0);
            VkOffset3D.y(srcOffset0, srcY0);
            VkOffset3D.z(srcOffset0, 0);
            VkOffset3D.x(srcOffset1, srcX1);
            VkOffset3D.y(srcOffset1, srcY1);
            VkOffset3D.z(srcOffset1, 1);

            MemorySegment dstSubresource = VkImageBlit.dstSubresource(region);
            VkImageSubresourceLayers.aspectMask(dstSubresource, VkImageAspectFlagBits.VK_IMAGE_ASPECT_COLOR_BIT.value());
            VkImageSubresourceLayers.mipLevel(dstSubresource, 0);
            VkImageSubresourceLayers.baseArrayLayer(dstSubresource, 0);
            VkImageSubresourceLayers.layerCount(dstSubresource, 1);

            MemorySegment dstOffsets = VkImageBlit.dstOffsets(region);
            MemorySegment dstOffset0 = dstOffsets.asSlice(0, VkOffset3D.sizeof());
            MemorySegment dstOffset1 = dstOffsets.asSlice(VkOffset3D.sizeof(), VkOffset3D.sizeof());
            VkOffset3D.x(dstOffset0, dstX0);
            VkOffset3D.y(dstOffset0, dstY0);
            VkOffset3D.z(dstOffset0, 0);
            VkOffset3D.x(dstOffset1, dstX1);
            VkOffset3D.y(dstOffset1, dstY1);
            VkOffset3D.z(dstOffset1, 1);

            VulkanFFM.vkCmdBlitImage(cmd, srcImage, srcLayout, dstImage, dstLayout, 1, region, filter);
        }
    }

    public static void blitImage(VkCommandBuffer cmd, MemorySegment srcImage, int srcLayout, MemorySegment dstImage, int dstLayout,
                                 int srcWidth, int srcHeight, int dstWidth, int dstHeight, int filter) {
        blitImage(cmd, srcImage, srcLayout, dstImage, dstLayout, 0, 0, srcWidth, srcHeight, 0, 0, dstWidth, dstHeight, filter);
    }

    public static void blitImage(MemorySegment cmd, MemorySegment srcImage, int srcLayout, MemorySegment dstImage, int dstLayout,
                                 int srcWidth, int srcHeight, int dstWidth, int dstHeight, int filter) {
        blitImage(cmd, srcImage, srcLayout, dstImage, dstLayout, 0, 0, srcWidth, srcHeight, 0, 0, dstWidth, dstHeight, filter);
    }

    // Reusable execution methods
    public void execute(VkCommandBuffer cmd) {
        execute(cmd.handle());
    }

    public void execute(MemorySegment cmd) {
        blitImage(cmd, srcImage, srcLayout, dstImage, dstLayout, srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, filter);
    }

    // Builder for fluent construction
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private MemorySegment srcImage;
        private int srcLayout = VkImageLayout.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL.value();
        private MemorySegment dstImage;
        private int dstLayout = VkImageLayout.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL.value();
        private int srcX0 = 0, srcY0 = 0, srcX1 = 0, srcY1 = 0;
        private int dstX0 = 0, dstY0 = 0, dstX1 = 0, dstY1 = 0;
        private int filter = VkFilter.VK_FILTER_LINEAR.value();

        private Builder() {
        }

        public Builder srcImage(MemorySegment image) {
            this.srcImage = image;
            return this;
        }

        public Builder srcLayout(int layout) {
            this.srcLayout = layout;
            return this;
        }

        public Builder dstImage(MemorySegment image) {
            this.dstImage = image;
            return this;
        }

        public Builder dstLayout(int layout) {
            this.dstLayout = layout;
            return this;
        }

        public Builder srcRegion(int x0, int y0, int x1, int y1) {
            this.srcX0 = x0;
            this.srcY0 = y0;
            this.srcX1 = x1;
            this.srcY1 = y1;
            return this;
        }

        public Builder dstRegion(int x0, int y0, int x1, int y1) {
            this.dstX0 = x0;
            this.dstY0 = y0;
            this.dstX1 = x1;
            this.dstY1 = y1;
            return this;
        }

        public Builder srcSize(int width, int height) {
            this.srcX1 = width;
            this.srcY1 = height;
            return this;
        }

        public Builder dstSize(int width, int height) {
            this.dstX1 = width;
            this.dstY1 = height;
            return this;
        }

        public Builder filter(int filter) {
            this.filter = filter;
            return this;
        }

        public Builder linear() {
            this.filter = VkFilter.VK_FILTER_LINEAR.value();
            return this;
        }

        public Builder nearest() {
            this.filter = VkFilter.VK_FILTER_NEAREST.value();
            return this;
        }

        public VkBlit build() {
            return new VkBlit(srcImage, srcLayout, dstImage, dstLayout, srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, filter);
        }

        public void blit(VkCommandBuffer cmd) {
            build().execute(cmd);
        }

        public void blit(MemorySegment cmd) {
            build().execute(cmd);
        }
    }
}