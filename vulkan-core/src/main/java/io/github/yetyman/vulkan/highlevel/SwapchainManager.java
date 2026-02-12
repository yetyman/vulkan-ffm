package io.github.yetyman.vulkan.highlevel;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.enums.*;
import java.lang.foreign.*;

/**
 * High-level manager for Vulkan swapchain and associated rendering resources.
 *
 * <p>A swapchain is Vulkan's mechanism for presenting rendered images to the screen. It maintains
 * a queue of images that cycle between GPU rendering and display presentation, preventing visual
 * tearing and enabling smooth frame updates. Common configurations include double buffering (2 images)
 * or triple buffering (3 images) for reduced input lag.
 *
 * <p>This manager bundles together the swapchain with its related resources:
 * <ul>
 *   <li>Swapchain - The image queue for presentation</li>
 *   <li>Image Views - Vulkan handles to access each swapchain image for rendering</li>
 *   <li>Synchronization Semaphores - Per-image semaphores for GPU synchronization:
 *     <ul>
 *       <li>imageAvailable - Signals when an image is ready to render into</li>
 *       <li>renderFinished - Signals when rendering is complete and ready to present</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p>Key features:
 * <ul>
 *   <li>Simplifies setup by creating all related resources together</li>
 *   <li>Handles swapchain recreation during window resize events</li>
 *   <li>Automatic resource cleanup via AutoCloseable</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * // Initial setup
 * try (var manager = VulkanSwapchainManager.builder()
 *         .context(vulkanContext)
 *         .surface(windowSurface)
 *         .extent(800, 600)
 *         .vsync(true)
 *         .minImageCount(3)
 *         .build()) {
 *
 *     // Render loop
 *     while (!shouldClose) {
 *         int imageIndex = acquireNextImage();
 *         SwapchainImage img = manager.getImage(imageIndex);
 *
 *         // Use img.imageAvailable() and img.renderFinished() for synchronization
 *         renderFrame(img);
 *         presentImage(imageIndex);
 *
 *         // Handle window resize
 *         if (windowResized) {
 *             manager.recreate(newWidth, newHeight);
 *         }
 *     }
 * }
 * }</pre>
 */
public class SwapchainManager implements AutoCloseable {
    private final Arena arena;
    private final VkDevice device;
    private final MemorySegment surface;
    private VkSwapchain swapchain;
    private SwapchainImage[] swapchainImages;
    private int width, height;
    
    private SwapchainManager(Arena arena, VkDevice device, MemorySegment surface, int width, int height,
                             VkSwapchain swapchain, SwapchainImage[] swapchainImages) {
        this.arena = arena;
        this.device = device;
        this.surface = surface;
        this.width = width;
        this.height = height;
        this.swapchain = swapchain;
        this.swapchainImages = swapchainImages;
    }
    
    /** @return a new builder for configuring swapchain manager creation */
    public static Builder builder() {
        return new Builder();
    }
    
    /** @return the swapchain */
    public VkSwapchain swapchain() { return swapchain; }
    
    /** @return the swapchain images */
    public SwapchainImage[] swapchainImages() { return swapchainImages; }
    
    /** @return specific swapchain image */
    public SwapchainImage getImage(int index) { return swapchainImages[index]; }
    
    /** @return current width */
    public int width() { return width; }
    
    /** @return current height */
    public int height() { return height; }
    
    /** @return number of swapchain images */
    public int imageCount() { return swapchainImages.length; }
    
    /** Recreates swapchain and related resources with new dimensions */
    public void recreate(int newWidth, int newHeight) {
        // Clean up old resources
        for (SwapchainImage si : swapchainImages) {
            si.close();
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
        
        // Recreate swapchain images
        MemorySegment[] images = swapchain.getImages();
        swapchainImages = new SwapchainImage[images.length];
        for (int i = 0; i < images.length; i++) {
            VkImageView imageView = VkImageView.builder()
                .device(device)
                .image(images[i])
                .viewType(VkImageViewType.VK_IMAGE_VIEW_TYPE_2D.value())
                .format(VkFormat.VK_FORMAT_B8G8R8A8_SRGB.value())
                .aspectMask(VkImageAspectFlagBits.VK_IMAGE_ASPECT_COLOR_BIT.value())
                .build(arena);
            
            VkSemaphore imageAvailable = VkSemaphore.builder()
                .device(device)
                .build(arena);
            VkSemaphore renderFinished = VkSemaphore.builder()
                .device(device)
                .build(arena);
            
            swapchainImages[i] = new SwapchainImage(images[i], imageView, imageAvailable, renderFinished);
        }
    }
    
    @Override
    public void close() {
        for (SwapchainImage si : swapchainImages) {
            si.close();
        }
        swapchain.close();
    }
    
    /**
     * Builder for swapchain manager creation.
     */
    public static class Builder {
        private Arena arena;
        private VkDevice device;
        private MemorySegment surface;
        private int width, height;
        private boolean vsync = true;
        private int minImageCount = 3;
        private int format = VkFormat.VK_FORMAT_B8G8R8A8_SRGB.value();
        
        private Builder() {}
        
        /** Sets the arena */
        public Builder arena(Arena arena) {
            this.arena = arena;
            return this;
        }
        
        /** Sets the device */
        public Builder device(VkDevice device) {
            this.device = device;
            return this;
        }
        
        /** Sets the Vulkan context */
        public Builder context(VulkanContext context) {
            this.arena = context.arena();
            this.device = context.device();
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
        public SwapchainManager build() {
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
                .format(format, VkColorSpaceKHR.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR.value())
                .build(arena);
            
            // Create swapchain images with semaphores
            MemorySegment[] images = swapchain.getImages();
            SwapchainImage[] swapchainImages = new SwapchainImage[images.length];
            for (int i = 0; i < images.length; i++) {
                VkImageView imageView = VkImageView.builder()
                    .device(device)
                    .image(images[i])
                    .viewType(VkImageViewType.VK_IMAGE_VIEW_TYPE_2D.value())
                    .format(format)
                    .aspectMask(VkImageAspectFlagBits.VK_IMAGE_ASPECT_COLOR_BIT.value())
                    .build(arena);
                
                VkSemaphore imageAvailable = VkSemaphore.builder()
                    .device(device)
                    .build(arena);
                VkSemaphore renderFinished = VkSemaphore.builder()
                    .device(device)
                    .build(arena);
                
                swapchainImages[i] = new SwapchainImage(images[i], imageView, imageAvailable, renderFinished);
            }
            
            return new SwapchainManager(arena, device, surface, width, height, swapchain, swapchainImages);
        }
    }

    /**
     * Encapsulates a swapchain image with its view and associated semaphores.
     */
    public record SwapchainImage(MemorySegment image, VkImageView imageView, VkSemaphore imageAvailableSemaphore,
                                 VkSemaphore renderFinishedSemaphore) implements AutoCloseable {
        @Override
        public void close() {
            imageView.close();
            imageAvailableSemaphore.close();
            renderFinishedSemaphore.close();
        }
    }
}