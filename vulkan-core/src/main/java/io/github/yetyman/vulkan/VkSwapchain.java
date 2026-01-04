package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;

/**
 * Wrapper for Vulkan swapchain (VkSwapchainKHR) with automatic resource management.
 * A swapchain manages a queue of images for presentation to a surface.
 * Implements AutoCloseable for use with try-with-resources.
 */
public class VkSwapchain implements AutoCloseable {
    private final MemorySegment handle;
    private final VkDevice device;
    private final MemorySegment[] images;
    
    private VkSwapchain(MemorySegment handle, VkDevice device, MemorySegment[] images) {
        this.handle = handle;
        this.device = device;
        this.images = images;
    }
    
    /**
     * Creates a new swapchain for the given surface and dimensions with VSync enabled.
     */
    public static VkSwapchain create(Arena arena, VkDevice device, MemorySegment surface, int width, int height) {
        return builder()
            .device(device)
            .surface(surface)
            .extent(width, height)
            .vsync(true)
            .build(arena);
    }
    
    /**
     * Creates a new swapchain for the given surface and dimensions.
     */
    public static VkSwapchain create(Arena arena, VkDevice device, MemorySegment surface, int width, int height, boolean vsync) {
        return builder()
            .device(device)
            .surface(surface)
            .extent(width, height)
            .vsync(vsync)
            .build(arena);
    }
    
    /** @return a new builder for configuring swapchain creation */
    public static Builder builder() {
        return new Builder();
    }
    
    /** @return the VkSwapchainKHR handle */
    public MemorySegment handle() { return handle; }
    
    /** @return array of VkImage handles from the swapchain */
    public MemorySegment[] getImages() { return images; }
    
    @Override
    public void close() {
        Vulkan.destroySwapchainKHR(device.handle(), handle);
    }
    
    /**
     * Builder for flexible swapchain creation.
     */
    public static class Builder {
        private VkDevice device;
        private MemorySegment surface;
        private int width, height;
        private int minImageCount = 3;
        private int imageFormat = VkFormat.VK_FORMAT_B8G8R8A8_SRGB.value();
        private int colorSpace = VkColorSpaceKHR.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR.value();
        private int imageArrayLayers = 1;
        private int imageUsage = VkImageUsageFlagBits.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT.value();
        private int imageSharingMode = 0;
        private int[] queueFamilyIndices = null;
        private int preTransform = VkSurfaceTransformFlagBitsKHR.VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR.value();
        private int compositeAlpha = VkCompositeAlphaFlagBitsKHR.VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR.value();
        private int presentMode = VkPresentModeKHR.VK_PRESENT_MODE_FIFO_KHR.value();
        private boolean clipped = true;
        private MemorySegment oldSwapchain = MemorySegment.NULL;
        private int flags = 0;
        
        private Builder() {}
        
        /** Sets the logical device */
        public Builder device(VkDevice device) {
            this.device = device;
            return this;
        }
        
        /** Sets the surface to present to */
        public Builder surface(MemorySegment surface) {
            this.surface = surface;
            return this;
        }
        
        /** Sets swapchain image dimensions */
        public Builder extent(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }
        
        /** Sets minimum number of images in swapchain */
        public Builder minImageCount(int count) {
            this.minImageCount = count;
            return this;
        }
        
        /** Sets image format and color space */
        public Builder format(int format, int colorSpace) {
            this.imageFormat = format;
            this.colorSpace = colorSpace;
            return this;
        }
        
        /** Sets image usage flags */
        public Builder imageUsage(int usage) {
            this.imageUsage = usage;
            return this;
        }
        
        /** Enables VSync (FIFO present mode) */
        public Builder vsync(boolean enable) {
            this.presentMode = enable ? VkPresentModeKHR.VK_PRESENT_MODE_FIFO_KHR.value() : VkPresentModeKHR.VK_PRESENT_MODE_IMMEDIATE_KHR.value();
            return this;
        }
        
        /** Sets present mode directly */
        public Builder presentMode(int mode) {
            this.presentMode = mode;
            return this;
        }
        
        /** Sets pre-transform */
        public Builder preTransform(int transform) {
            this.preTransform = transform;
            return this;
        }
        
        /** Sets composite alpha */
        public Builder compositeAlpha(int alpha) {
            this.compositeAlpha = alpha;
            return this;
        }
        
        /** Sets old swapchain for recreation */
        public Builder oldSwapchain(MemorySegment oldSwapchain) {
            this.oldSwapchain = oldSwapchain;
            return this;
        }
        
        /** Sets creation flags */
        public Builder flags(int flags) {
            this.flags = flags;
            return this;
        }
        
        /** Creates the swapchain */
        public VkSwapchain build(Arena arena) {
            if (device == null) throw new IllegalStateException("device not set");
            if (surface == null) throw new IllegalStateException("surface not set");
            if (width <= 0 || height <= 0) throw new IllegalStateException("invalid extent");
            
            MemorySegment createInfo = VkSwapchainCreateInfoKHR.allocate(arena);
            VkSwapchainCreateInfoKHR.sType(createInfo, 1000001000); // VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR
            VkSwapchainCreateInfoKHR.pNext(createInfo, MemorySegment.NULL);
            VkSwapchainCreateInfoKHR.flags(createInfo, flags);
            VkSwapchainCreateInfoKHR.surface(createInfo, surface);
            VkSwapchainCreateInfoKHR.minImageCount(createInfo, minImageCount);
            VkSwapchainCreateInfoKHR.imageFormat(createInfo, imageFormat);
            VkSwapchainCreateInfoKHR.imageColorSpace(createInfo, colorSpace);
            MemorySegment imageExtent = VkSwapchainCreateInfoKHR.imageExtent(createInfo);
            VkExtent2D.width(imageExtent, width);
            VkExtent2D.height(imageExtent, height);
            VkSwapchainCreateInfoKHR.imageArrayLayers(createInfo, imageArrayLayers);
            VkSwapchainCreateInfoKHR.imageUsage(createInfo, imageUsage);
            VkSwapchainCreateInfoKHR.imageSharingMode(createInfo, imageSharingMode);
            
            if (queueFamilyIndices != null && queueFamilyIndices.length > 0) {
                MemorySegment indicesArray = arena.allocate(ValueLayout.JAVA_INT, queueFamilyIndices.length);
                for (int i = 0; i < queueFamilyIndices.length; i++) {
                    indicesArray.setAtIndex(ValueLayout.JAVA_INT, i, queueFamilyIndices[i]);
                }
                VkSwapchainCreateInfoKHR.queueFamilyIndexCount(createInfo, queueFamilyIndices.length);
                VkSwapchainCreateInfoKHR.pQueueFamilyIndices(createInfo, indicesArray);
            } else {
                VkSwapchainCreateInfoKHR.queueFamilyIndexCount(createInfo, 0);
                VkSwapchainCreateInfoKHR.pQueueFamilyIndices(createInfo, MemorySegment.NULL);
            }
            
            VkSwapchainCreateInfoKHR.preTransform(createInfo, preTransform);
            VkSwapchainCreateInfoKHR.compositeAlpha(createInfo, compositeAlpha);
            VkSwapchainCreateInfoKHR.presentMode(createInfo, presentMode);
            VkSwapchainCreateInfoKHR.clipped(createInfo, clipped ? 1 : 0);
            VkSwapchainCreateInfoKHR.oldSwapchain(createInfo, oldSwapchain);
            
            MemorySegment swapchainPtr = arena.allocate(ValueLayout.ADDRESS);
            Vulkan.createSwapchainKHR(device.handle(), createInfo, swapchainPtr).check();
            MemorySegment swapchain = swapchainPtr.get(ValueLayout.ADDRESS, 0);
            
            MemorySegment imageCount = arena.allocate(ValueLayout.JAVA_INT);
            Vulkan.getSwapchainImagesKHR(device.handle(), swapchain, imageCount, MemorySegment.NULL).check();
            int count = imageCount.get(ValueLayout.JAVA_INT, 0);
            
            MemorySegment[] images = new MemorySegment[count];
            MemorySegment imagesArray = arena.allocate(ValueLayout.ADDRESS, count);
            Vulkan.getSwapchainImagesKHR(device.handle(), swapchain, imageCount, imagesArray).check();
            for (int i = 0; i < count; i++) {
                images[i] = imagesArray.getAtIndex(ValueLayout.ADDRESS, i);
            }
            
            return new VkSwapchain(swapchain, device, images);
        }
    }
}