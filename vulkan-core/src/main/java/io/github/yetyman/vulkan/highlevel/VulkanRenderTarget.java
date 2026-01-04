package io.github.yetyman.vulkan.highlevel;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;

/**
 * Manages a render target (image + memory + view).
 */
public class VulkanRenderTarget implements AutoCloseable {
    private final Arena arena;
    private final VkDevice device;
    private final VkPhysicalDevice physicalDevice;
    private final VkImage image;
    private final VkImageView imageView;

    
    private VulkanRenderTarget(Arena arena, VkDevice device, VkPhysicalDevice physicalDevice,
                              int format, int width, int height, int usage, int aspectMask) {
        this.arena = arena;
        this.device = device;
        this.physicalDevice = physicalDevice;
        
        // Create image
        MemorySegment imageInfo = VkImageCreateInfo.allocate(arena);
        VkImageCreateInfo.sType(imageInfo, VkStructureType.VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO.value());
        VkImageCreateInfo.imageType(imageInfo, VkImageType.VK_IMAGE_TYPE_2D.value());
        VkImageCreateInfo.format(imageInfo, format);
        MemorySegment extent = VkImageCreateInfo.extent(imageInfo);
        VkExtent3D.width(extent, width);
        VkExtent3D.height(extent, height);
        VkExtent3D.depth(extent, 1);
        VkImageCreateInfo.mipLevels(imageInfo, 1);
        VkImageCreateInfo.arrayLayers(imageInfo, 1);
        VkImageCreateInfo.samples(imageInfo, VkSampleCountFlagBits.VK_SAMPLE_COUNT_1_BIT.value());
        VkImageCreateInfo.tiling(imageInfo, VkImageTiling.VK_IMAGE_TILING_OPTIMAL.value());
        VkImageCreateInfo.usage(imageInfo, usage);
        VkImageCreateInfo.sharingMode(imageInfo, VkSharingMode.VK_SHARING_MODE_EXCLUSIVE.value());
        VkImageCreateInfo.initialLayout(imageInfo, VkImageLayout.VK_IMAGE_LAYOUT_UNDEFINED.value());
        
        // Create image using builder
        image = VkImage.builder()
            .device(device)
            .dimensions(width, height, 1)
            .format(format)
            .usage(usage)
            .build(arena);
        

        
        // Create image view
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

    
    @Override
    public void close() {
        imageView.close();
        image.close();
        // Memory is freed by VkImage.close()
    }
    
    public static class Builder {
        private Arena arena;
        private VkDevice device;
        private VkPhysicalDevice physicalDevice;
        private int format;
        private int width, height;
        private int usage;
        private int aspectMask = VkImageAspectFlagBits.VK_IMAGE_ASPECT_COLOR_BIT.value();
        
        public Builder arena(Arena arena) {
            this.arena = arena;
            return this;
        }
        
        public Builder device(VkDevice device) {
            this.device = device;
            return this;
        }
        
        public Builder physicalDevice(VkPhysicalDevice physicalDevice) {
            this.physicalDevice = physicalDevice;
            return this;
        }
        
        public Builder context(VulkanContext context) {
            this.arena = context.arena();
            this.device = context.device();
            this.physicalDevice = context.physicalDevice();
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
        
        public VulkanRenderTarget build() {
            if (arena == null) throw new IllegalStateException("arena not set");
            if (device == null) throw new IllegalStateException("device not set");
            if (width <= 0 || height <= 0) throw new IllegalStateException("invalid extent");
            return new VulkanRenderTarget(arena, device, physicalDevice, format, width, height, usage, aspectMask);
        }
    }
}