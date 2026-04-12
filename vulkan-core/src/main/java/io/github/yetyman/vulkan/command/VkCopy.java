package io.github.yetyman.vulkan.command;

import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.VkBuffer;
import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;

/**
 * Vulkan copy command wrapper supporting buffer-to-buffer, buffer-to-image, and image-to-buffer copies.
 */
public record VkCopy(MemorySegment src, MemorySegment dst, long srcOffset, long dstOffset, long size, CopyType type) {
    
    public enum CopyType { BUFFER_TO_BUFFER, BUFFER_TO_IMAGE, IMAGE_TO_BUFFER, IMAGE_TO_IMAGE }
    
    // Static helpers for buffer copies (zero allocation)
    public static void copyBuffer(VkCommandBuffer cmd, MemorySegment srcBuffer, MemorySegment dstBuffer, long size) {
        copyBuffer(cmd.handle(), srcBuffer, dstBuffer, 0, 0, size);
    }
    
    public static void copyBuffer(MemorySegment cmd, MemorySegment srcBuffer, MemorySegment dstBuffer, long size) {
        copyBuffer(cmd, srcBuffer, dstBuffer, 0, 0, size);
    }
    
    public static void copyBuffer(VkCommandBuffer cmd, MemorySegment srcBuffer, MemorySegment dstBuffer, long srcOffset, long dstOffset, long size) {
        copyBuffer(cmd.handle(), srcBuffer, dstBuffer, srcOffset, dstOffset, size);
    }
    
    public static void copyBuffer(MemorySegment cmd, MemorySegment srcBuffer, MemorySegment dstBuffer, long srcOffset, long dstOffset, long size) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment copyRegion = VkBufferCopy.allocate(arena);
            VkBufferCopy.srcOffset(copyRegion, srcOffset);
            VkBufferCopy.dstOffset(copyRegion, dstOffset);
            VkBufferCopy.size(copyRegion, size);
            VulkanFFM.vkCmdCopyBuffer(cmd, srcBuffer, dstBuffer, 1, copyRegion);
        }
    }
    
    // VkBuffer overloads
    public static void copyBuffer(VkCommandBuffer cmd, VkBuffer srcBuffer, VkBuffer dstBuffer, long size) {
        copyBuffer(cmd, srcBuffer.handle(), dstBuffer.handle(), size);
    }
    
    public static void copyBuffer(MemorySegment cmd, VkBuffer srcBuffer, VkBuffer dstBuffer, long size) {
        copyBuffer(cmd, srcBuffer.handle(), dstBuffer.handle(), size);
    }
    
    public static void copyBuffer(VkCommandBuffer cmd, VkBuffer srcBuffer, VkBuffer dstBuffer, long srcOffset, long dstOffset, long size) {
        copyBuffer(cmd, srcBuffer.handle(), dstBuffer.handle(), srcOffset, dstOffset, size);
    }
    
    public static void copyBuffer(MemorySegment cmd, VkBuffer srcBuffer, VkBuffer dstBuffer, long srcOffset, long dstOffset, long size) {
        copyBuffer(cmd, srcBuffer.handle(), dstBuffer.handle(), srcOffset, dstOffset, size);
    }
    
    // Static helpers for buffer-to-image copies
    public static void copyBufferToImage(VkCommandBuffer cmd, MemorySegment buffer, MemorySegment image, int imageLayout, int width, int height) {
        copyBufferToImage(cmd.handle(), buffer, image, imageLayout, width, height, 1);
    }
    
    public static void copyBufferToImage(MemorySegment cmd, MemorySegment buffer, MemorySegment image, int imageLayout, int width, int height) {
        copyBufferToImage(cmd, buffer, image, imageLayout, width, height, 1);
    }
    
    public static void copyBufferToImage(VkCommandBuffer cmd, MemorySegment buffer, MemorySegment image, int imageLayout, int width, int height, int depth) {
        copyBufferToImage(cmd.handle(), buffer, image, imageLayout, width, height, depth);
    }
    
    public static void copyBufferToImage(MemorySegment cmd, MemorySegment buffer, MemorySegment image, int imageLayout, int width, int height, int depth) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment region = VkBufferImageCopy.allocate(arena);
            VkBufferImageCopy.bufferOffset(region, 0);
            VkBufferImageCopy.bufferRowLength(region, 0);
            VkBufferImageCopy.bufferImageHeight(region, 0);
            
            MemorySegment imageSubresource = VkBufferImageCopy.imageSubresource(region);
            VkImageSubresourceLayers.aspectMask(imageSubresource, VkImageAspectFlagBits.VK_IMAGE_ASPECT_COLOR_BIT.value());
            VkImageSubresourceLayers.mipLevel(imageSubresource, 0);
            VkImageSubresourceLayers.baseArrayLayer(imageSubresource, 0);
            VkImageSubresourceLayers.layerCount(imageSubresource, 1);
            
            MemorySegment imageOffset = VkBufferImageCopy.imageOffset(region);
            VkOffset3D.x(imageOffset, 0);
            VkOffset3D.y(imageOffset, 0);
            VkOffset3D.z(imageOffset, 0);
            
            MemorySegment imageExtent = VkBufferImageCopy.imageExtent(region);
            VkExtent3D.width(imageExtent, width);
            VkExtent3D.height(imageExtent, height);
            VkExtent3D.depth(imageExtent, depth);
            
            VulkanFFM.vkCmdCopyBufferToImage(cmd, buffer, image, imageLayout, 1, region);
        }
    }
    
    // Static helpers for image-to-buffer copies
    public static void copyImageToBuffer(VkCommandBuffer cmd, MemorySegment image, int imageLayout, MemorySegment buffer, int width, int height) {
        copyImageToBuffer(cmd.handle(), image, imageLayout, buffer, width, height, 1);
    }
    
    public static void copyImageToBuffer(MemorySegment cmd, MemorySegment image, int imageLayout, MemorySegment buffer, int width, int height) {
        copyImageToBuffer(cmd, image, imageLayout, buffer, width, height, 1);
    }
    
    public static void copyImageToBuffer(VkCommandBuffer cmd, MemorySegment image, int imageLayout, MemorySegment buffer, int width, int height, int depth) {
        copyImageToBuffer(cmd.handle(), image, imageLayout, buffer, width, height, depth);
    }
    
    public static void copyImageToBuffer(MemorySegment cmd, MemorySegment image, int imageLayout, MemorySegment buffer, int width, int height, int depth) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment region = VkBufferImageCopy.allocate(arena);
            VkBufferImageCopy.bufferOffset(region, 0);
            VkBufferImageCopy.bufferRowLength(region, 0);
            VkBufferImageCopy.bufferImageHeight(region, 0);
            
            MemorySegment imageSubresource = VkBufferImageCopy.imageSubresource(region);
            VkImageSubresourceLayers.aspectMask(imageSubresource, VkImageAspectFlagBits.VK_IMAGE_ASPECT_COLOR_BIT.value());
            VkImageSubresourceLayers.mipLevel(imageSubresource, 0);
            VkImageSubresourceLayers.baseArrayLayer(imageSubresource, 0);
            VkImageSubresourceLayers.layerCount(imageSubresource, 1);
            
            MemorySegment imageOffset = VkBufferImageCopy.imageOffset(region);
            VkOffset3D.x(imageOffset, 0);
            VkOffset3D.y(imageOffset, 0);
            VkOffset3D.z(imageOffset, 0);
            
            MemorySegment imageExtent = VkBufferImageCopy.imageExtent(region);
            VkExtent3D.width(imageExtent, width);
            VkExtent3D.height(imageExtent, height);
            VkExtent3D.depth(imageExtent, depth);
            
            VulkanFFM.vkCmdCopyImageToBuffer(cmd, image, imageLayout, buffer, 1, region);
        }
    }
    
    // Static helpers for image-to-image copies
    public static void copyImage(VkCommandBuffer cmd, MemorySegment srcImage, int srcLayout, MemorySegment dstImage, int dstLayout, int width, int height) {
        copyImage(cmd.handle(), srcImage, srcLayout, dstImage, dstLayout, width, height, 1);
    }
    
    public static void copyImage(MemorySegment cmd, MemorySegment srcImage, int srcLayout, MemorySegment dstImage, int dstLayout, int width, int height) {
        copyImage(cmd, srcImage, srcLayout, dstImage, dstLayout, width, height, 1);
    }
    
    public static void copyImage(VkCommandBuffer cmd, MemorySegment srcImage, int srcLayout, MemorySegment dstImage, int dstLayout, int width, int height, int depth) {
        copyImage(cmd.handle(), srcImage, srcLayout, dstImage, dstLayout, width, height, depth);
    }
    
    public static void copyImage(MemorySegment cmd, MemorySegment srcImage, int srcLayout, MemorySegment dstImage, int dstLayout, int width, int height, int depth) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment region = VkImageCopy.allocate(arena);
            
            MemorySegment srcSubresource = VkImageCopy.srcSubresource(region);
            VkImageSubresourceLayers.aspectMask(srcSubresource, VkImageAspectFlagBits.VK_IMAGE_ASPECT_COLOR_BIT.value());
            VkImageSubresourceLayers.mipLevel(srcSubresource, 0);
            VkImageSubresourceLayers.baseArrayLayer(srcSubresource, 0);
            VkImageSubresourceLayers.layerCount(srcSubresource, 1);
            
            MemorySegment srcOffset = VkImageCopy.srcOffset(region);
            VkOffset3D.x(srcOffset, 0); VkOffset3D.y(srcOffset, 0); VkOffset3D.z(srcOffset, 0);
            
            MemorySegment dstSubresource = VkImageCopy.dstSubresource(region);
            VkImageSubresourceLayers.aspectMask(dstSubresource, VkImageAspectFlagBits.VK_IMAGE_ASPECT_COLOR_BIT.value());
            VkImageSubresourceLayers.mipLevel(dstSubresource, 0);
            VkImageSubresourceLayers.baseArrayLayer(dstSubresource, 0);
            VkImageSubresourceLayers.layerCount(dstSubresource, 1);
            
            MemorySegment dstOffset = VkImageCopy.dstOffset(region);
            VkOffset3D.x(dstOffset, 0); VkOffset3D.y(dstOffset, 0); VkOffset3D.z(dstOffset, 0);
            
            MemorySegment extent = VkImageCopy.extent(region);
            VkExtent3D.width(extent, width);
            VkExtent3D.height(extent, height);
            VkExtent3D.depth(extent, depth);
            
            VulkanFFM.vkCmdCopyImage(cmd, srcImage, srcLayout, dstImage, dstLayout, 1, region);
        }
    }
    
    // Reusable execution methods
    public void execute(VkCommandBuffer cmd) {
        execute(cmd.handle());
    }
    
    public void execute(MemorySegment cmd) {
        switch (type) {
            case BUFFER_TO_BUFFER -> copyBuffer(cmd, src, dst, srcOffset, dstOffset, size);
            // TODO: Add IMAGE_TO_BUFFER, BUFFER_TO_IMAGE, IMAGE_TO_IMAGE cases
            default -> throw new UnsupportedOperationException("Copy type not implemented: " + type);
        }
    }
    
    // Builder for fluent construction
    public static Builder builder() { return new Builder(); }
    
    public static class Builder {
        private MemorySegment src;
        private MemorySegment dst;
        private long srcOffset = 0;
        private long dstOffset = 0;
        private long size = 0;
        private CopyType type = CopyType.BUFFER_TO_BUFFER;
        
        private Builder() {}
        
        public Builder src(MemorySegment src) { this.src = src; return this; }
        public Builder src(VkBuffer src) { this.src = src.handle(); return this; }
        public Builder dst(MemorySegment dst) { this.dst = dst; return this; }
        public Builder dst(VkBuffer dst) { this.dst = dst.handle(); return this; }
        public Builder srcOffset(long offset) { this.srcOffset = offset; return this; }
        public Builder dstOffset(long offset) { this.dstOffset = offset; return this; }
        public Builder size(long size) { this.size = size; return this; }
        public Builder type(CopyType type) { this.type = type; return this; }
        
        public VkCopy build() {
            return new VkCopy(src, dst, srcOffset, dstOffset, size, type);
        }
        
        public void copy(VkCommandBuffer cmd) {
            build().execute(cmd);
        }
        
        public void copy(MemorySegment cmd) {
            build().execute(cmd);
        }
    }
}