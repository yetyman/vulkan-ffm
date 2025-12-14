package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;
import static io.github.yetyman.vulkan.VkConstants.*;

/**
 * Wrapper for Vulkan image view (VkImageView) with automatic resource management.
 * Image views describe how to access an image and which part of the image to access.
 */
public class VkImageView implements AutoCloseable {
    private final MemorySegment handle;
    private final MemorySegment device;
    
    private VkImageView(MemorySegment handle, MemorySegment device) {
        this.handle = handle;
        this.device = device;
    }
    
    /**
     * Creates an image view for the given image.
     * @param arena memory arena for allocations
     * @param device the VkDevice handle
     * @param image the VkImage handle to create a view for
     * @return a new VkImageView instance
     */
    public static VkImageView create(Arena arena, MemorySegment device, MemorySegment image) {
        MemorySegment createInfo = VkImageViewCreateInfo.allocate(arena);
        VkImageViewCreateInfo.sType(createInfo, VkStructureType.VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO);
        VkImageViewCreateInfo.pNext(createInfo, MemorySegment.NULL);
        VkImageViewCreateInfo.flags(createInfo, 0);
        VkImageViewCreateInfo.image(createInfo, image);
        VkImageViewCreateInfo.viewType(createInfo, 1); // VK_IMAGE_VIEW_TYPE_2D
        VkImageViewCreateInfo.format(createInfo, VK_FORMAT_B8G8R8A8_SRGB);
        MemorySegment components = VkImageViewCreateInfo.components(createInfo);
        VkComponentMapping.r(components, 0);
        VkComponentMapping.g(components, 0);
        VkComponentMapping.b(components, 0);
        VkComponentMapping.a(components, 0);
        
        MemorySegment subresourceRange = VkImageViewCreateInfo.subresourceRange(createInfo);
        VkImageSubresourceRange.aspectMask(subresourceRange, 0x00000004); // VK_IMAGE_ASPECT_COLOR_BIT
        VkImageSubresourceRange.baseMipLevel(subresourceRange, 0);
        VkImageSubresourceRange.levelCount(subresourceRange, 1);
        VkImageSubresourceRange.baseArrayLayer(subresourceRange, 0);
        VkImageSubresourceRange.layerCount(subresourceRange, 1);
        
        MemorySegment imageViewPtr = arena.allocate(ValueLayout.ADDRESS);
        VulkanExtensions.createImageView(device, createInfo, imageViewPtr).check();
        return new VkImageView(imageViewPtr.get(ValueLayout.ADDRESS, 0), device);
    }
    
    /** @return the VkImageView handle */
    public MemorySegment handle() { return handle; }
    
    @Override
    public void close() {
        VulkanExtensions.destroyImageView(device, handle);
    }
}