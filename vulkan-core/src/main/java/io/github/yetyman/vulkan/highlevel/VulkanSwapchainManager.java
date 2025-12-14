package io.github.yetyman.vulkan.highlevel;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.enums.*;
import java.lang.foreign.*;

/**
 * High-level manager for swapchain, image views, and framebuffers.
 * Handles the common pattern of creating these related resources together.
 */
public class VulkanSwapchainManager implements AutoCloseable {
    private final Arena arena;
    private final MemorySegment device;
    private final MemorySegment surface;
    private VkSwapchain swapchain;
    private VkImageView[] imageViews;
    private VkFramebuffer[] framebuffers;
    private int width, height;
    
    private VulkanSwapchainManager(Arena arena, MemorySegment device, MemorySegment surface, int width, int height,
                                  VkSwapchain swapchain, VkImageView[] imageViews) {
        this.arena = arena;
        this.device = device;
        this.surface = surface;
        this.width = width;
        this.height = height;
        this.swapchain = swapchain;
        this.imageViews = imageViews;
    }
    
    /** @return a new builder for configuring swapchain manager creation */
    public static Builder builder() {
        return new Builder();
    }
    
    /** @return the swapchain */
    public VkSwapchain swapchain() { return swapchain; }
    
    /** @return the image views */
    public VkImageView[] imageViews() { return imageViews; }
    
    /** @return the framebuffers (null if not created) */
    public VkFramebuffer[] framebuffers() { return framebuffers; }
    
    /** @return current width */
    public int width() { return width; }
    
    /** @return current height */
    public int height() { return height; }
    
    /** @return number of swapchain images */
    public int imageCount() { return imageViews.length; }
    
    /** Creates framebuffers for the given render pass */
    public void createFramebuffers(MemorySegment renderPass, MemorySegment... additionalAttachments) {
        if (framebuffers != null) {
            for (VkFramebuffer fb : framebuffers) {
                fb.close();
            }
        }
        
        framebuffers = new VkFramebuffer[imageViews.length];
        for (int i = 0; i < imageViews.length; i++) {
            VkFramebuffer.Builder builder = VkFramebuffer.builder()
                .device(device)
                .renderPass(renderPass)
                .attachment(imageViews[i].handle())
                .dimensions(width, height);
            
            for (MemorySegment attachment : additionalAttachments) {
                builder.attachment(attachment);
            }
            
            framebuffers[i] = builder.build(arena);
        }
    }
    
    /** Recreates swapchain and related resources with new dimensions */
    public void recreate(int newWidth, int newHeight) {
        // Clean up old resources
        if (framebuffers != null) {
            for (VkFramebuffer fb : framebuffers) {
                fb.close();
            }
            framebuffers = null;
        }
        for (VkImageView iv : imageViews) {
            iv.close();
        }
        swapchain.close();
        
        // Update dimensions
        width = newWidth;
        height = newHeight;
        
        // Recreate swapchain
        swapchain = VkSwapchain.builder()
            .device(device)
            .surface(surface)
            .extent(width, height)
            .build(arena);
        
        // Recreate image views
        MemorySegment[] images = swapchain.getImages();
        imageViews = new VkImageView[images.length];
        for (int i = 0; i < images.length; i++) {
            imageViews[i] = VkImageView.builder()
                .device(device)
                .image(images[i])
                .viewType(VkImageViewType.VK_IMAGE_VIEW_TYPE_2D)
                .format(VkFormat.VK_FORMAT_B8G8R8A8_SRGB)
                .aspectMask(VkImageAspectFlagBits.VK_IMAGE_ASPECT_COLOR_BIT)
                .build(arena);
        }
    }
    
    @Override
    public void close() {
        if (framebuffers != null) {
            for (VkFramebuffer fb : framebuffers) {
                fb.close();
            }
        }
        for (VkImageView iv : imageViews) {
            iv.close();
        }
        swapchain.close();
    }
    
    /**
     * Builder for swapchain manager creation.
     */
    public static class Builder {
        private Arena arena;
        private MemorySegment device;
        private MemorySegment surface;
        private int width, height;
        private boolean vsync = true;
        private int minImageCount = 3;
        private int format = VkFormat.VK_FORMAT_B8G8R8A8_SRGB;
        
        private Builder() {}
        
        /** Sets the arena */
        public Builder arena(Arena arena) {
            this.arena = arena;
            return this;
        }
        
        /** Sets the device */
        public Builder device(MemorySegment device) {
            this.device = device;
            return this;
        }
        
        /** Sets the Vulkan context */
        public Builder context(VulkanContext context) {
            this.arena = context.arena();
            this.device = context.device().handle();
            return this;
        }
        
        /** Sets the surface */
        public Builder surface(MemorySegment surface) {
            this.surface = surface;
            return this;
        }
        
        /** Sets dimensions */
        public Builder extent(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }
        
        /** Sets VSync */
        public Builder vsync(boolean vsync) {
            this.vsync = vsync;
            return this;
        }
        
        /** Sets minimum image count */
        public Builder minImageCount(int count) {
            this.minImageCount = count;
            return this;
        }
        
        /** Sets image format */
        public Builder format(int format) {
            this.format = format;
            return this;
        }
        
        /** Creates the swapchain manager */
        public VulkanSwapchainManager build() {
            if (arena == null) throw new IllegalStateException("arena not set");
            if (device == null) throw new IllegalStateException("device not set");
            if (surface == null) throw new IllegalStateException("surface not set");
            if (width <= 0 || height <= 0) throw new IllegalStateException("invalid extent");
            
            // Create swapchain
            VkSwapchain swapchain = VkSwapchain.builder()
                .device(device)
                .surface(surface)
                .extent(width, height)
                .vsync(vsync)
                .minImageCount(minImageCount)
                .format(format, VkColorSpaceKHR.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR)
                .build(arena);
            
            // Create image views
            MemorySegment[] images = swapchain.getImages();
            VkImageView[] imageViews = new VkImageView[images.length];
            for (int i = 0; i < images.length; i++) {
                imageViews[i] = VkImageView.builder()
                    .device(device)
                    .image(images[i])
                    .viewType(VkImageViewType.VK_IMAGE_VIEW_TYPE_2D)
                    .format(format)
                    .aspectMask(VkImageAspectFlagBits.VK_IMAGE_ASPECT_COLOR_BIT)
                    .build(arena);
            }
            
            return new VulkanSwapchainManager(arena, device, surface, width, height, swapchain, imageViews);
        }
    }
}