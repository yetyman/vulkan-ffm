package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;

/**
 * Wrapper for Vulkan image view (VkImageView) with automatic resource management.
 * Image views describe how to access an image and which part of the image to access.
 */
public class VkImageView implements AutoCloseable {
    private final MemorySegment handle;
    private final MemorySegment device;
    
    // Package-private constructor for VkFramebufferAttachment
    VkImageView(MemorySegment handle, MemorySegment device) {
        this.handle = handle;
        this.device = device;
    }
    
    /**
     * Creates a 2D color image view with default settings.
     * @param arena memory arena for allocations
     * @param device the VkDevice handle
     * @param image the VkImage handle to create a view for
     * @return a new VkImageView instance
     */
    public static VkImageView create(Arena arena, MemorySegment device, MemorySegment image) {
        return builder()
            .device(device)
            .image(image)
            .viewType(VkImageViewType.VK_IMAGE_VIEW_TYPE_2D)
            .format(VkFormat.VK_FORMAT_B8G8R8A8_SRGB)
            .aspectMask(VkImageAspectFlagBits.VK_IMAGE_ASPECT_COLOR_BIT)
            .build(arena);
    }
    
    /** @return a new builder for configuring image view creation */
    public static Builder builder() {
        return new Builder();
    }
    
    /** @return the VkImageView handle */
    public MemorySegment handle() { return handle; }
    
    @Override
    public void close() {
        VulkanExtensions.destroyImageView(device, handle);
    }
    
    /**
     * Builder for flexible image view creation.
     */
    public static class Builder {
        private MemorySegment device;
        private MemorySegment image;
        private int viewType = VkImageViewType.VK_IMAGE_VIEW_TYPE_2D;
        private int format = VkFormat.VK_FORMAT_B8G8R8A8_SRGB;
        private int aspectMask = VkImageAspectFlagBits.VK_IMAGE_ASPECT_COLOR_BIT;
        private int baseMipLevel = 0;
        private int levelCount = 1;
        private int baseArrayLayer = 0;
        private int layerCount = 1;
        private int flags = 0;
        private boolean identitySwizzle = true;
        
        private Builder() {}
        
        /** Sets the logical device */
        public Builder device(MemorySegment device) {
            this.device = device;
            return this;
        }
        
        /** Sets the image to create a view for */
        public Builder image(MemorySegment image) {
            this.image = image;
            return this;
        }
        
        /** Sets the view type (1D, 2D, 3D, Cube, Array) */
        public Builder viewType(int viewType) {
            this.viewType = viewType;
            return this;
        }
        
        /** Sets the image format */
        public Builder format(int format) {
            this.format = format;
            return this;
        }
        
        /** Sets the aspect mask (color, depth, stencil) */
        public Builder aspectMask(int aspectMask) {
            this.aspectMask = aspectMask;
            return this;
        }
        
        /** Sets mip level range */
        public Builder mipLevels(int baseMipLevel, int levelCount) {
            this.baseMipLevel = baseMipLevel;
            this.levelCount = levelCount;
            return this;
        }
        
        /** Sets array layer range */
        public Builder arrayLayers(int baseArrayLayer, int layerCount) {
            this.baseArrayLayer = baseArrayLayer;
            this.layerCount = layerCount;
            return this;
        }
        
        /** Sets creation flags */
        public Builder flags(int flags) {
            this.flags = flags;
            return this;
        }
        
        /** Disables identity swizzle (allows custom component mapping) */
        public Builder customSwizzle() {
            this.identitySwizzle = false;
            return this;
        }
        
        /** Creates the image view */
        public VkImageView build(Arena arena) {
            if (device == null) throw new IllegalStateException("device not set");
            if (image == null) throw new IllegalStateException("image not set");
            
            MemorySegment createInfo = VkImageViewCreateInfo.allocate(arena);
            VkImageViewCreateInfo.sType(createInfo, VkStructureType.VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO);
            VkImageViewCreateInfo.pNext(createInfo, MemorySegment.NULL);
            VkImageViewCreateInfo.flags(createInfo, flags);
            VkImageViewCreateInfo.image(createInfo, image);
            VkImageViewCreateInfo.viewType(createInfo, viewType);
            VkImageViewCreateInfo.format(createInfo, format);
            
            MemorySegment components = VkImageViewCreateInfo.components(createInfo);
            int swizzle = identitySwizzle ? 0 : 1;
            VkComponentMapping.r(components, swizzle);
            VkComponentMapping.g(components, swizzle);
            VkComponentMapping.b(components, swizzle);
            VkComponentMapping.a(components, swizzle);
            
            MemorySegment subresourceRange = VkImageViewCreateInfo.subresourceRange(createInfo);
            VkImageSubresourceRange.aspectMask(subresourceRange, aspectMask);
            VkImageSubresourceRange.baseMipLevel(subresourceRange, baseMipLevel);
            VkImageSubresourceRange.levelCount(subresourceRange, levelCount);
            VkImageSubresourceRange.baseArrayLayer(subresourceRange, baseArrayLayer);
            VkImageSubresourceRange.layerCount(subresourceRange, layerCount);
            
            MemorySegment imageViewPtr = arena.allocate(ValueLayout.ADDRESS);
            VulkanExtensions.createImageView(device, createInfo, imageViewPtr).check();
            return new VkImageView(imageViewPtr.get(ValueLayout.ADDRESS, 0), device);
        }
    }
}