package io.github.yetyman.vulkan.highlevel;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.enums.*;
import java.lang.foreign.*;

/**
 * Manages a render target (image + memory + view).
 * Supports MSAA via the sampleCount builder option.
 */
public class VulkanRenderTarget implements AutoCloseable {
    private final Arena arena;
    private final VkDevice device;
    private final VkImage image;
    private final VkImageView imageView;
    private final int sampleCount;

    private VulkanRenderTarget(Arena arena, VkDevice device,
                              int format, int width, int height, int usage, int aspectMask, int sampleCount) {
        this.arena = arena;
        this.device = device;
        this.sampleCount = sampleCount;

        image = VkImage.builder()
            .device(device)
            .dimensions(width, height, 1)
            .format(format)
            .usage(usage)
            .samples(sampleCount)
            .build(arena);

        imageView = VkImageView.builder()
            .device(device)
            .image(image.handle())
            .viewType(VkImageViewType.VK_IMAGE_VIEW_TYPE_2D.value())
            .format(format)
            .aspectMask(aspectMask)
            .build(arena);
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public VkImage image() { return image; }
    public VkImageView imageView() { return imageView; }
    /** @return the MSAA sample count for this render target */
    public int sampleCount() { return sampleCount; }

    
    @Override
    public void close() {
        imageView.close();
        image.close();
        // Memory is freed by VkImage.close()
    }
    
    public static class Builder {
        private Arena arena;
        private VkDevice device;
        private int format;
        private int width, height;
        private int usage;
        private int aspectMask = VkImageAspectFlagBits.VK_IMAGE_ASPECT_COLOR_BIT.value();
        private int sampleCount = VkSampleCountFlagBits.VK_SAMPLE_COUNT_1_BIT.value();
        
        public Builder arena(Arena arena) {
            this.arena = arena;
            return this;
        }
        
        public Builder device(VkDevice device) {
            this.device = device;
            return this;
        }
        
        public Builder context(VulkanContext context) {
            this.arena = context.arena();
            this.device = context.device();
            return this;
        }
        
        public Builder format(int format) {
            this.format = format;
            return this;
        }
        
        public Builder extent(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }
        
        public Builder usage(int usage) {
            this.usage = usage;
            return this;
        }
        
        public Builder aspectMask(int aspectMask) {
            this.aspectMask = aspectMask;
            return this;
        }

        /** Sets the MSAA sample count (default: VK_SAMPLE_COUNT_1_BIT = no MSAA). */
        public Builder sampleCount(int sampleCount) {
            this.sampleCount = sampleCount;
            return this;
        }
        
        public VulkanRenderTarget build() {
            if (arena == null) throw new IllegalStateException("arena not set");
            if (device == null) throw new IllegalStateException("device not set");
            if (width <= 0 || height <= 0) throw new IllegalStateException("invalid extent");
            return new VulkanRenderTarget(arena, device, format, width, height, usage, aspectMask, sampleCount);
        }
    }
}