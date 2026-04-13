package io.github.yetyman.vulkan.command;

import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;

import java.lang.foreign.*;

/**
 * Vulkan clear command wrapper for clearing color and depth/stencil images.
 */
public record VkClear(MemorySegment image, ClearType type, float[] colorValues, float depthValue, int stencilValue,
                      int baseMipLevel, int levelCount, int baseArrayLayer, int layerCount, int aspectMask) {

    public enum ClearType {COLOR_IMAGE, DEPTH_STENCIL_IMAGE}

    // Static helpers for color image clearing
    public static void clearColorImage(VkCommandBuffer cmd, MemorySegment image, int imageLayout, float r, float g, float b, float a) {
        clearColorImage(cmd.handle(), image, imageLayout, r, g, b, a, 0, 1, 0, 1);
    }

    public static void clearColorImage(MemorySegment cmd, MemorySegment image, int imageLayout, float r, float g, float b, float a) {
        clearColorImage(cmd, image, imageLayout, r, g, b, a, 0, 1, 0, 1);
    }

    public static void clearColorImage(VkCommandBuffer cmd, MemorySegment image, int imageLayout, float r, float g, float b, float a,
                                       int baseMipLevel, int levelCount, int baseArrayLayer, int layerCount) {
        clearColorImage(cmd.handle(), image, imageLayout, r, g, b, a, baseMipLevel, levelCount, baseArrayLayer, layerCount);
    }

    public static void clearColorImage(MemorySegment cmd, MemorySegment image, int imageLayout, float r, float g, float b, float a,
                                       int baseMipLevel, int levelCount, int baseArrayLayer, int layerCount) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment clearColor = VkClearColorValue.allocate(arena);
            MemorySegment floatArray = VkClearColorValue.float32(clearColor);
            floatArray.setAtIndex(ValueLayout.JAVA_FLOAT, 0, r);
            floatArray.setAtIndex(ValueLayout.JAVA_FLOAT, 1, g);
            floatArray.setAtIndex(ValueLayout.JAVA_FLOAT, 2, b);
            floatArray.setAtIndex(ValueLayout.JAVA_FLOAT, 3, a);

            MemorySegment range = VkImageSubresourceRange.allocate(arena);
            VkImageSubresourceRange.aspectMask(range, VkImageAspectFlagBits.VK_IMAGE_ASPECT_COLOR_BIT.value());
            VkImageSubresourceRange.baseMipLevel(range, baseMipLevel);
            VkImageSubresourceRange.levelCount(range, levelCount);
            VkImageSubresourceRange.baseArrayLayer(range, baseArrayLayer);
            VkImageSubresourceRange.layerCount(range, layerCount);

            VulkanFFM.vkCmdClearColorImage(cmd, image, imageLayout, clearColor, 1, range);
        }
    }

    // Static helpers for depth/stencil image clearing
    public static void clearDepthStencilImage(VkCommandBuffer cmd, MemorySegment image, int imageLayout, float depth, int stencil) {
        clearDepthStencilImage(cmd.handle(), image, imageLayout, depth, stencil, 0, 1, 0, 1);
    }

    public static void clearDepthStencilImage(MemorySegment cmd, MemorySegment image, int imageLayout, float depth, int stencil) {
        clearDepthStencilImage(cmd, image, imageLayout, depth, stencil, 0, 1, 0, 1);
    }

    public static void clearDepthStencilImage(VkCommandBuffer cmd, MemorySegment image, int imageLayout, float depth, int stencil,
                                              int baseMipLevel, int levelCount, int baseArrayLayer, int layerCount) {
        clearDepthStencilImage(cmd.handle(), image, imageLayout, depth, stencil, baseMipLevel, levelCount, baseArrayLayer, layerCount);
    }

    public static void clearDepthStencilImage(MemorySegment cmd, MemorySegment image, int imageLayout, float depth, int stencil,
                                              int baseMipLevel, int levelCount, int baseArrayLayer, int layerCount) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment clearDepthStencil = VkClearDepthStencilValue.allocate(arena);
            VkClearDepthStencilValue.depth(clearDepthStencil, depth);
            VkClearDepthStencilValue.stencil(clearDepthStencil, stencil);

            MemorySegment range = VkImageSubresourceRange.allocate(arena);
            VkImageSubresourceRange.aspectMask(range, VkImageAspectFlagBits.VK_IMAGE_ASPECT_DEPTH_BIT.value() |
                    VkImageAspectFlagBits.VK_IMAGE_ASPECT_STENCIL_BIT.value());
            VkImageSubresourceRange.baseMipLevel(range, baseMipLevel);
            VkImageSubresourceRange.levelCount(range, levelCount);
            VkImageSubresourceRange.baseArrayLayer(range, baseArrayLayer);
            VkImageSubresourceRange.layerCount(range, layerCount);

            VulkanFFM.vkCmdClearDepthStencilImage(cmd, image, imageLayout, clearDepthStencil, 1, range);
        }
    }

    // Reusable execution methods
    public void execute(VkCommandBuffer cmd) {
        execute(cmd.handle());
    }

    public void execute(MemorySegment cmd) {
        switch (type) {
            case COLOR_IMAGE -> {
                if (colorValues.length >= 4) {
                    clearColorImage(cmd, image, VkImageLayout.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL.value(),
                            colorValues[0], colorValues[1], colorValues[2], colorValues[3],
                            baseMipLevel, levelCount, baseArrayLayer, layerCount);
                }
            }
            case DEPTH_STENCIL_IMAGE -> {
                clearDepthStencilImage(cmd, image, VkImageLayout.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL.value(),
                        depthValue, stencilValue, baseMipLevel, levelCount, baseArrayLayer, layerCount);
            }
            default -> throw new UnsupportedOperationException("Clear type not implemented: " + type);
        }
    }

    // Builder for fluent construction
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private MemorySegment image;
        private ClearType type;
        private float[] colorValues = new float[4];
        private float depthValue = 1.0f;
        private int stencilValue = 0;
        private int baseMipLevel = 0;
        private int levelCount = 1;
        private int baseArrayLayer = 0;
        private int layerCount = 1;
        private int aspectMask = VkImageAspectFlagBits.VK_IMAGE_ASPECT_COLOR_BIT.value();

        private Builder() {
        }

        public Builder image(MemorySegment image) {
            this.image = image;
            return this;
        }

        public Builder colorImage() {
            this.type = ClearType.COLOR_IMAGE;
            return this;
        }

        public Builder depthStencilImage() {
            this.type = ClearType.DEPTH_STENCIL_IMAGE;
            return this;
        }

        public Builder color(float r, float g, float b, float a) {
            this.colorValues = new float[]{r, g, b, a};
            return this;
        }

        public Builder depth(float depth) {
            this.depthValue = depth;
            return this;
        }

        public Builder stencil(int stencil) {
            this.stencilValue = stencil;
            return this;
        }

        public Builder mipLevels(int baseMipLevel, int levelCount) {
            this.baseMipLevel = baseMipLevel;
            this.levelCount = levelCount;
            return this;
        }

        public Builder arrayLayers(int baseArrayLayer, int layerCount) {
            this.baseArrayLayer = baseArrayLayer;
            this.layerCount = layerCount;
            return this;
        }

        public Builder aspectMask(int aspectMask) {
            this.aspectMask = aspectMask;
            return this;
        }

        public VkClear build() {
            return new VkClear(image, type, colorValues, depthValue, stencilValue,
                    baseMipLevel, levelCount, baseArrayLayer, layerCount, aspectMask);
        }

        public void clear(VkCommandBuffer cmd) {
            build().execute(cmd);
        }

        public void clear(MemorySegment cmd) {
            build().execute(cmd);
        }
    }
}