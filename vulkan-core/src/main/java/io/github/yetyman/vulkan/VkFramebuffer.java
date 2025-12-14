package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;

/**
 * Wrapper for Vulkan framebuffer (VkFramebuffer) with automatic resource management.
 * A framebuffer represents a collection of attachments used by a render pass.
 */
public class VkFramebuffer implements AutoCloseable {
    private final MemorySegment handle;
    private final MemorySegment device;
    
    private VkFramebuffer(MemorySegment handle, MemorySegment device) {
        this.handle = handle;
        this.device = device;
    }
    
    /**
     * Creates a framebuffer with a single image view attachment.
     * @param arena memory arena for allocations
     * @param device the VkDevice handle
     * @param renderPass the VkRenderPass handle this framebuffer is compatible with
     * @param imageView the VkImageView handle to use as the color attachment
     * @param width framebuffer width in pixels
     * @param height framebuffer height in pixels
     * @return a new VkFramebuffer instance
     */
    public static VkFramebuffer create(Arena arena, MemorySegment device, MemorySegment renderPass, MemorySegment imageView, int width, int height) {
        MemorySegment attachments = arena.allocate(ValueLayout.ADDRESS);
        attachments.set(ValueLayout.ADDRESS, 0, imageView);
        
        MemorySegment framebufferInfo = VkFramebufferCreateInfo.allocate(arena);
        VkFramebufferCreateInfo.sType(framebufferInfo, VkStructureType.VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO);
        VkFramebufferCreateInfo.pNext(framebufferInfo, MemorySegment.NULL);
        VkFramebufferCreateInfo.flags(framebufferInfo, 0);
        VkFramebufferCreateInfo.renderPass(framebufferInfo, renderPass);
        VkFramebufferCreateInfo.attachmentCount(framebufferInfo, 1);
        VkFramebufferCreateInfo.pAttachments(framebufferInfo, attachments);
        VkFramebufferCreateInfo.width(framebufferInfo, width);
        VkFramebufferCreateInfo.height(framebufferInfo, height);
        VkFramebufferCreateInfo.layers(framebufferInfo, 1);
        
        MemorySegment framebufferPtr = arena.allocate(ValueLayout.ADDRESS);
        VulkanExtensions.createFramebuffer(device, framebufferInfo, framebufferPtr).check();
        return new VkFramebuffer(framebufferPtr.get(ValueLayout.ADDRESS, 0), device);
    }
    
    /** @return the VkFramebuffer handle */
    public MemorySegment handle() { return handle; }
    
    @Override
    public void close() {
        VulkanExtensions.destroyFramebuffer(device, handle);
    }
}