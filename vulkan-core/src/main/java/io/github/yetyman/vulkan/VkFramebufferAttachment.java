package io.github.yetyman.vulkan;

import java.lang.foreign.*;

/**
 * Represents a framebuffer attachment with metadata about its type and properties.
 */
public class VkFramebufferAttachment {
    private final VkImageView imageView;
    private final AttachmentType type;
    private final int format;
    private final int index;
    
    public VkFramebufferAttachment(VkImageView imageView, AttachmentType type, int format, int index) {
        this.imageView = imageView;
        this.type = type;
        this.format = format;
        this.index = index;
    }
    
    public VkFramebufferAttachment(MemorySegment imageViewHandle, AttachmentType type, int format, int index) {
        this.imageView = new VkImageView(imageViewHandle, null); // Create wrapper with null device for compatibility
        this.type = type;
        this.format = format;
        this.index = index;
    }
    
    public VkFramebufferAttachment(io.github.yetyman.vulkan.highlevel.VkTexture texture, AttachmentType type, int index) {
        this.imageView = new VkImageView(texture.imageView(), null);
        this.type = type;
        this.format = texture.format();
        this.index = index;
    }
    
    /** @return the image view */
    public VkImageView imageView() { return imageView; }
    
    /** @return the image view handle */
    public MemorySegment imageViewHandle() { return imageView.handle(); }
    
    /** @return the attachment type */
    public AttachmentType type() { return type; }
    
    /** @return the image format */
    public int format() { return format; }
    
    /** @return the attachment index (for color attachments) */
    public int index() { return index; }
    
    /** @return true if this is a color attachment */
    public boolean isColor() { return type == AttachmentType.COLOR; }
    
    /** @return true if this is a depth attachment */
    public boolean isDepth() { return type == AttachmentType.DEPTH; }
    
    /** @return true if this is a stencil attachment */
    public boolean isStencil() { return type == AttachmentType.STENCIL; }
    
    /** @return true if this is a depth-stencil attachment */
    public boolean isDepthStencil() { return type == AttachmentType.DEPTH_STENCIL; }
    
    public enum AttachmentType {
        COLOR, DEPTH, STENCIL, DEPTH_STENCIL
    }
}