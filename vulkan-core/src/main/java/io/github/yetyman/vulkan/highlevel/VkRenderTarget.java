package io.github.yetyman.vulkan.highlevel;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;
import java.util.*;

/**
 * Render target abstraction with automatic resize handling and multiple attachments.
 * 
 * Example usage:
 * <pre>{@code
 * // Create render target for offscreen rendering
 * VkRenderTarget renderTarget = VkRenderTarget.builder()
 *     .device(device)
 *     .renderPass(renderPass)
 *     .allocator(memoryAllocator)
 *     .size(1024, 768)
 *     .colorAttachment(0, VK_FORMAT_R8G8B8A8_UNORM)
 *     .depthAttachment(VK_FORMAT_D32_SFLOAT)
 *     .build(arena);
 * 
 * // Render to target
 * renderTarget.beginRenderPass(commandBuffer, 
 *     new float[]{0.2f, 0.3f, 0.8f, 1.0f}, // Clear color
 *     1.0f); // Clear depth
 * 
 * // Draw scene...
 * pipeline.bind(commandBuffer);
 * mesh.draw(commandBuffer);
 * 
 * renderTarget.endRenderPass(commandBuffer);
 * 
 * // Handle window resize
 * renderTarget.resize(newWidth, newHeight, memoryAllocator);
 * 
 * // Access attachments for post-processing
 * MemorySegment colorTexture = renderTarget.getColorAttachment(0);
 * }</pre>
 */
public class VkRenderTarget implements AutoCloseable {
    private final MemorySegment device;
    private final MemorySegment renderPass;
    private final Arena arena;
    private final List<Attachment> attachments = new ArrayList<>();
    private VkFramebuffer framebuffer;
    private int width, height;
    
    private VkRenderTarget(MemorySegment device, MemorySegment renderPass, Arena arena, int width, int height) {
        this.device = device;
        this.renderPass = renderPass;
        this.arena = arena;
        this.width = width;
        this.height = height;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    /** @return framebuffer handle */
    public MemorySegment handle() { return framebuffer.handle(); }
    
    /** @return framebuffer width */
    public int width() { return width; }
    
    /** @return framebuffer height */
    public int height() { return height; }
    
    /** @return color attachment at index */
    public MemorySegment getColorAttachment(int index) {
        return attachments.stream()
            .filter(a -> a.type == AttachmentType.COLOR && a.index == index)
            .map(a -> a.imageView)
            .findFirst()
            .orElse(MemorySegment.NULL);
    }
    
    /** @return depth attachment */
    public MemorySegment getDepthAttachment() {
        return attachments.stream()
            .filter(a -> a.type == AttachmentType.DEPTH)
            .map(a -> a.imageView)
            .findFirst()
            .orElse(MemorySegment.NULL);
    }
    
    /**
     * Resizes the framebuffer and recreates all attachments.
     */
    public void resize(int newWidth, int newHeight, VkMemoryAllocator allocator) {
        if (newWidth == width && newHeight == height) return;
        
        if (framebuffer != null) {
            framebuffer.close();
        }
        
        for (Attachment attachment : attachments) {
            attachment.resize(newWidth, newHeight, allocator);
        }
        
        this.width = newWidth;
        this.height = newHeight;
        
        createFramebuffer();
    }
    
    /**
     * Begins a render pass with this framebuffer.
     */
    public void beginRenderPass(MemorySegment commandBuffer, float[] clearColor, float clearDepth) {
        MemorySegment clearValues = arena.allocate(io.github.yetyman.vulkan.generated.VkClearValue.layout(), attachments.size());
        
        for (int i = 0; i < attachments.size(); i++) {
            Attachment attachment = attachments.get(i);
            MemorySegment clearValue = clearValues.asSlice(i * io.github.yetyman.vulkan.generated.VkClearValue.layout().byteSize(), io.github.yetyman.vulkan.generated.VkClearValue.layout());
            
            if (attachment.type == AttachmentType.COLOR) {
                MemorySegment color = io.github.yetyman.vulkan.generated.VkClearValue.color(clearValue);
                VkClearColorValue.float32(color, 0, clearColor.length > 0 ? clearColor[0] : 0.0f);
                VkClearColorValue.float32(color, 1, clearColor.length > 1 ? clearColor[1] : 0.0f);
                VkClearColorValue.float32(color, 2, clearColor.length > 2 ? clearColor[2] : 0.0f);
                VkClearColorValue.float32(color, 3, clearColor.length > 3 ? clearColor[3] : 1.0f);
            } else if (attachment.type == AttachmentType.DEPTH) {
                MemorySegment depthStencil = io.github.yetyman.vulkan.generated.VkClearValue.depthStencil(clearValue);
                VkClearDepthStencilValue.depth(depthStencil, clearDepth);
                VkClearDepthStencilValue.stencil(depthStencil, 0);
            }
        }
        
        MemorySegment renderPassBegin = VkRenderPassBeginInfo.allocate(arena);
        VkRenderPassBeginInfo.sType(renderPassBegin, VkStructureType.VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO);
        VkRenderPassBeginInfo.renderPass(renderPassBegin, renderPass);
        VkRenderPassBeginInfo.framebuffer(renderPassBegin, framebuffer.handle());
        
        MemorySegment renderArea = VkRenderPassBeginInfo.renderArea(renderPassBegin);
        VkOffset2D.x(io.github.yetyman.vulkan.generated.VkRect2D.offset(renderArea), 0);
        VkOffset2D.y(io.github.yetyman.vulkan.generated.VkRect2D.offset(renderArea), 0);
        VkExtent2D.width(io.github.yetyman.vulkan.generated.VkRect2D.extent(renderArea), width);
        VkExtent2D.height(io.github.yetyman.vulkan.generated.VkRect2D.extent(renderArea), height);
        
        VkRenderPassBeginInfo.clearValueCount(renderPassBegin, attachments.size());
        VkRenderPassBeginInfo.pClearValues(renderPassBegin, clearValues);
        
        VulkanExtensions.cmdBeginRenderPass(commandBuffer, renderPassBegin, VkSubpassContents.VK_SUBPASS_CONTENTS_INLINE);
    }
    
    /**
     * Ends the render pass.
     */
    public void endRenderPass(MemorySegment commandBuffer) {
        VulkanExtensions.cmdEndRenderPass(commandBuffer);
    }
    
    private void createFramebuffer() {
        this.framebuffer = VkFramebuffer.builder()
            .device(device)
            .renderPass(renderPass)
            .attachments(attachments.stream().map(a -> a.imageView).toArray(MemorySegment[]::new))
            .dimensions(width, height)
            .build(arena);
    }
    
    @Override
    public void close() {
        if (framebuffer != null) {
            framebuffer.close();
        }
        for (Attachment attachment : attachments) {
            attachment.close();
        }
        attachments.clear();
    }
    
    private enum AttachmentType { COLOR, DEPTH }
    
    private class Attachment implements AutoCloseable {
        final AttachmentType type;
        final int index;
        final int format;
        VkImage image;
        MemorySegment imageView;
        VkAllocation allocation;
        VkMemoryAllocator allocator;
        
        Attachment(AttachmentType type, int index, int format) {
            this.type = type;
            this.index = index;
            this.format = format;
        }
        
        void create(int width, int height, VkMemoryAllocator allocator) {
            this.allocator = allocator;
            int usage = type == AttachmentType.COLOR ? 
                VkImageUsageFlagBits.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT :
                VkImageUsageFlagBits.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT;
            
            image = VkImage.builder()
                .device(device)
                .dimensions(width, height, 1)
                .format(format)
                .mipLevels(1)
                .arrayLayers(1)
                .samples(VkSampleCountFlagBits.VK_SAMPLE_COUNT_1_BIT)
                .usage(usage)
                .build(arena);
            
            allocation = allocator.allocateImage(image.handle(), VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
            VulkanExtensions.bindImageMemory(device, image.handle(), allocation.memory(), allocation.offset()).check();
            
            MemorySegment imageViewInfo = VkImageViewCreateInfo.allocate(arena);
            VkImageViewCreateInfo.sType(imageViewInfo, VkStructureType.VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO);
            VkImageViewCreateInfo.image(imageViewInfo, image.handle());
            VkImageViewCreateInfo.viewType(imageViewInfo, VkImageViewType.VK_IMAGE_VIEW_TYPE_2D);
            VkImageViewCreateInfo.format(imageViewInfo, format);
            
            MemorySegment subresourceRange = VkImageViewCreateInfo.subresourceRange(imageViewInfo);
            VkImageSubresourceRange.aspectMask(subresourceRange, 
                type == AttachmentType.COLOR ? VkImageAspectFlagBits.VK_IMAGE_ASPECT_COLOR_BIT : 
                VkImageAspectFlagBits.VK_IMAGE_ASPECT_DEPTH_BIT);
            VkImageSubresourceRange.baseMipLevel(subresourceRange, 0);
            VkImageSubresourceRange.levelCount(subresourceRange, 1);
            VkImageSubresourceRange.baseArrayLayer(subresourceRange, 0);
            VkImageSubresourceRange.layerCount(subresourceRange, 1);
            
            MemorySegment imageViewPtr = arena.allocate(ValueLayout.ADDRESS);
            VulkanExtensions.createImageView(device, imageViewInfo, imageViewPtr).check();
            this.imageView = imageViewPtr.get(ValueLayout.ADDRESS, 0);
        }
        
        void resize(int newWidth, int newHeight, VkMemoryAllocator allocator) {
            close();
            create(newWidth, newHeight, allocator);
        }
        
        @Override
        public void close() {
            if (imageView != null && !imageView.equals(MemorySegment.NULL)) {
                VulkanExtensions.destroyImageView(device, imageView);
            }
            if (image != null) {
                image.close();
            }
            if (allocator != null && allocation != null) {
                allocator.free(allocation);
            }
        }
    }
    
    public static class Builder {
        // Builder implementation would go here
        public VkRenderTarget build(Arena arena) {
            throw new UnsupportedOperationException("Builder not implemented");
        }
    }
}