package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.enums.VkStructureType;
import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;
import java.util.*;

/**
 * Wrapper for Vulkan framebuffer (VkFramebuffer) with automatic resource management.
 * A framebuffer represents a collection of attachments used by a render pass.
 */
public class VkFramebuffer implements AutoCloseable {
    private final MemorySegment handle;
    private final VkDevice device;
    private final List<VkFramebufferAttachment> attachments;
    
    private VkFramebuffer(MemorySegment handle, VkDevice device, List<VkFramebufferAttachment> attachments) {
        this.handle = handle;
        this.device = device;
        this.attachments = new ArrayList<>(attachments);
    }
    
    /** @return a new builder for configuring framebuffer creation */
    public static Builder builder() {
        return new Builder();
    }
    
    /** @return the VkFramebuffer handle */
    public MemorySegment handle() { return handle; }
    
    /** @return all attachments */
    public List<VkFramebufferAttachment> attachments() { return new ArrayList<>(attachments); }
    
    /** @return color attachment at the specified index */
    public VkFramebufferAttachment getColorAttachment(int index) {
        return attachments.stream()
            .filter(a -> a.isColor() && a.index() == index)
            .findFirst()
            .orElse(null);
    }
    
    /** @return depth attachment */
    public VkFramebufferAttachment getDepthAttachment() {
        return attachments.stream()
            .filter(VkFramebufferAttachment::isDepth)
            .findFirst()
            .orElse(null);
    }
    
    /** @return depth-stencil attachment */
    public VkFramebufferAttachment getDepthStencilAttachment() {
        return attachments.stream()
            .filter(VkFramebufferAttachment::isDepthStencil)
            .findFirst()
            .orElse(null);
    }
    
    @Override
    public void close() {
        Vulkan.destroyFramebuffer(device.handle(), handle);
    }
    
    /**
     * Builder for flexible framebuffer creation.
     */
    public static class Builder {
        private VkDevice device;
        private MemorySegment renderPass;
        private final List<VkFramebufferAttachment> attachments = new ArrayList<>();
        private int width;
        private int height;
        private int layers = 1;
        private int flags = 0;
        
        private Builder() {}
        
        /** Sets the logical device */
        public Builder device(VkDevice device) {
            this.device = device;
            return this;
        }
        
        /** Sets the render pass this framebuffer is compatible with */
        public Builder renderPass(MemorySegment renderPass) {
            this.renderPass = renderPass;
            return this;
        }
        
        /** Adds an attachment with metadata */
        public Builder attachment(VkFramebufferAttachment attachment) {
            this.attachments.add(attachment);
            return this;
        }
        
        /** Adds an attachment using VkImageView with explicit type */
        public Builder attachment(VkImageView imageView, VkFramebufferAttachment.AttachmentType type) {
            this.attachments.add(new VkFramebufferAttachment(imageView, type, 0, attachments.size()));
            return this;
        }
        
        /** Adds an attachment using MemorySegment with explicit type */
        public Builder attachment(MemorySegment imageView, VkFramebufferAttachment.AttachmentType type) {
            this.attachments.add(new VkFramebufferAttachment(new VkImageView(imageView, device), 
                type, 0, attachments.size()));
            return this;
        }
        
        /** Sets multiple attachments from imageViews with explicit types */
        public Builder attachments(MemorySegment[] imageViews, VkFramebufferAttachment.AttachmentType[] types) {
            if (imageViews.length != types.length) {
                throw new IllegalArgumentException("imageViews and types arrays must have same length");
            }
            this.attachments.clear();
            for (int i = 0; i < imageViews.length; i++) {
                attachment(imageViews[i], types[i]);
            }
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
            if (attachments.isEmpty()) throw new IllegalStateException("attachments not set");
            if (width <= 0 || height <= 0) throw new IllegalStateException("invalid dimensions");
            
            MemorySegment attachmentArray = arena.allocate(ValueLayout.ADDRESS, attachments.size());
            for (int i = 0; i < attachments.size(); i++) {
                attachmentArray.setAtIndex(ValueLayout.ADDRESS, i, attachments.get(i).imageViewHandle());
            }
            
            MemorySegment framebufferInfo = VkFramebufferCreateInfo.allocate(arena);
            VkFramebufferCreateInfo.sType(framebufferInfo, VkStructureType.VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO.value());
            VkFramebufferCreateInfo.pNext(framebufferInfo, MemorySegment.NULL);
            VkFramebufferCreateInfo.flags(framebufferInfo, flags);
            VkFramebufferCreateInfo.renderPass(framebufferInfo, renderPass);
            VkFramebufferCreateInfo.attachmentCount(framebufferInfo, attachments.size());
            VkFramebufferCreateInfo.pAttachments(framebufferInfo, attachmentArray);
            VkFramebufferCreateInfo.width(framebufferInfo, width);
            VkFramebufferCreateInfo.height(framebufferInfo, height);
            VkFramebufferCreateInfo.layers(framebufferInfo, layers);
            
            MemorySegment framebufferPtr = arena.allocate(ValueLayout.ADDRESS);
            Vulkan.createFramebuffer(device.handle(), framebufferInfo, framebufferPtr).check();
            return new VkFramebuffer(framebufferPtr.get(ValueLayout.ADDRESS, 0), device, attachments);
        }
    }
}