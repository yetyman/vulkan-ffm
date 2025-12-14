package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.enums.VkStructureType;
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
        return builder()
            .device(device)
            .renderPass(renderPass)
            .attachment(imageView)
            .dimensions(width, height)
            .build(arena);
    }
    
    /** @return a new builder for configuring framebuffer creation */
    public static Builder builder() {
        return new Builder();
    }
    
    /** @return the VkFramebuffer handle */
    public MemorySegment handle() { return handle; }
    
    @Override
    public void close() {
        VulkanExtensions.destroyFramebuffer(device, handle);
    }
    
    /**
     * Builder for flexible framebuffer creation.
     */
    public static class Builder {
        private MemorySegment device;
        private MemorySegment renderPass;
        private MemorySegment[] attachments;
        private int width;
        private int height;
        private int layers = 1;
        private int flags = 0;
        
        private Builder() {}
        
        /** Sets the logical device */
        public Builder device(MemorySegment device) {
            this.device = device;
            return this;
        }
        
        /** Sets the render pass this framebuffer is compatible with */
        public Builder renderPass(MemorySegment renderPass) {
            this.renderPass = renderPass;
            return this;
        }
        
        /** Sets a single attachment (color, depth, etc.) */
        public Builder attachment(MemorySegment imageView) {
            this.attachments = new MemorySegment[] { imageView };
            return this;
        }
        
        /** Sets multiple attachments in order (color, depth, etc.) */
        public Builder attachments(MemorySegment... imageViews) {
            this.attachments = imageViews;
            return this;
        }
        
        /** Sets framebuffer dimensions */
        public Builder dimensions(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }
        
        /** Sets framebuffer width */
        public Builder width(int width) {
            this.width = width;
            return this;
        }
        
        /** Sets framebuffer height */
        public Builder height(int height) {
            this.height = height;
            return this;
        }
        
        /** Sets number of layers for layered rendering (default: 1) */
        public Builder layers(int layers) {
            this.layers = layers;
            return this;
        }
        
        /** Sets creation flags (default: 0) */
        public Builder flags(int flags) {
            this.flags = flags;
            return this;
        }
        
        /** Creates the framebuffer */
        public VkFramebuffer build(Arena arena) {
            if (device == null) throw new IllegalStateException("device not set");
            if (renderPass == null) throw new IllegalStateException("renderPass not set");
            if (attachments == null || attachments.length == 0) throw new IllegalStateException("attachments not set");
            if (width <= 0 || height <= 0) throw new IllegalStateException("invalid dimensions");
            
            MemorySegment attachmentArray = arena.allocate(ValueLayout.ADDRESS, attachments.length);
            for (int i = 0; i < attachments.length; i++) {
                attachmentArray.setAtIndex(ValueLayout.ADDRESS, i, attachments[i]);
            }
            
            MemorySegment framebufferInfo = VkFramebufferCreateInfo.allocate(arena);
            VkFramebufferCreateInfo.sType(framebufferInfo, VkStructureType.VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO);
            VkFramebufferCreateInfo.pNext(framebufferInfo, MemorySegment.NULL);
            VkFramebufferCreateInfo.flags(framebufferInfo, flags);
            VkFramebufferCreateInfo.renderPass(framebufferInfo, renderPass);
            VkFramebufferCreateInfo.attachmentCount(framebufferInfo, attachments.length);
            VkFramebufferCreateInfo.pAttachments(framebufferInfo, attachmentArray);
            VkFramebufferCreateInfo.width(framebufferInfo, width);
            VkFramebufferCreateInfo.height(framebufferInfo, height);
            VkFramebufferCreateInfo.layers(framebufferInfo, layers);
            
            MemorySegment framebufferPtr = arena.allocate(ValueLayout.ADDRESS);
            VulkanExtensions.createFramebuffer(device, framebufferInfo, framebufferPtr).check();
            return new VkFramebuffer(framebufferPtr.get(ValueLayout.ADDRESS, 0), device);
        }
    }
}