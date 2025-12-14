package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;
import static io.github.yetyman.vulkan.VkConstants.*;

public class VkSwapchain implements AutoCloseable {
    private final MemorySegment handle;
    private final MemorySegment device;
    private final MemorySegment[] images;
    
    private VkSwapchain(MemorySegment handle, MemorySegment device, MemorySegment[] images) {
        this.handle = handle;
        this.device = device;
        this.images = images;
    }
    
    public static VkSwapchain create(Arena arena, MemorySegment device, MemorySegment surface, int width, int height) {
        MemorySegment createInfo = VkSwapchainCreateInfoKHR.allocate(arena);
        VkSwapchainCreateInfoKHR.sType(createInfo, 1000001000); // VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR
        VkSwapchainCreateInfoKHR.pNext(createInfo, MemorySegment.NULL);
        VkSwapchainCreateInfoKHR.flags(createInfo, 0);
        VkSwapchainCreateInfoKHR.surface(createInfo, surface);
        VkSwapchainCreateInfoKHR.minImageCount(createInfo, 3);
        VkSwapchainCreateInfoKHR.imageFormat(createInfo, VK_FORMAT_B8G8R8A8_SRGB);
        VkSwapchainCreateInfoKHR.imageColorSpace(createInfo, VK_COLOR_SPACE_SRGB_NONLINEAR_KHR);
        MemorySegment imageExtent = VkSwapchainCreateInfoKHR.imageExtent(createInfo);
        VkExtent2D.width(imageExtent, width);
        VkExtent2D.height(imageExtent, height);
        VkSwapchainCreateInfoKHR.imageArrayLayers(createInfo, 1);
        VkSwapchainCreateInfoKHR.imageUsage(createInfo, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT);
        VkSwapchainCreateInfoKHR.imageSharingMode(createInfo, 0);
        VkSwapchainCreateInfoKHR.queueFamilyIndexCount(createInfo, 0);
        VkSwapchainCreateInfoKHR.pQueueFamilyIndices(createInfo, MemorySegment.NULL);
        VkSwapchainCreateInfoKHR.preTransform(createInfo, VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR);
        VkSwapchainCreateInfoKHR.compositeAlpha(createInfo, VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR);
        VkSwapchainCreateInfoKHR.presentMode(createInfo, VK_PRESENT_MODE_FIFO_KHR);
        VkSwapchainCreateInfoKHR.clipped(createInfo, 1);
        VkSwapchainCreateInfoKHR.oldSwapchain(createInfo, MemorySegment.NULL);
        
        MemorySegment swapchainPtr = arena.allocate(ValueLayout.ADDRESS);
        VulkanExtensions.createSwapchainKHR(device, createInfo, swapchainPtr).check();
        MemorySegment swapchain = swapchainPtr.get(ValueLayout.ADDRESS, 0);
        
        MemorySegment imageCount = arena.allocate(ValueLayout.JAVA_INT);
        VulkanExtensions.getSwapchainImagesKHR(device, swapchain, imageCount, MemorySegment.NULL).check();
        int count = imageCount.get(ValueLayout.JAVA_INT, 0);
        
        MemorySegment[] images = new MemorySegment[count];
        MemorySegment imagesArray = arena.allocate(ValueLayout.ADDRESS, count);
        VulkanExtensions.getSwapchainImagesKHR(device, swapchain, imageCount, imagesArray).check();
        for (int i = 0; i < count; i++) {
            images[i] = imagesArray.getAtIndex(ValueLayout.ADDRESS, i);
        }
        
        return new VkSwapchain(swapchain, device, images);
    }
    
    public MemorySegment handle() { return handle; }
    public MemorySegment[] getImages() { return images; }
    
    @Override
    public void close() {
        VulkanExtensions.destroySwapchainKHR(device, handle);
    }
}