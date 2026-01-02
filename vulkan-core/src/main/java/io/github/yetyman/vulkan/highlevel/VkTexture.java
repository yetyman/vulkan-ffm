package io.github.yetyman.vulkan.highlevel;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;

/**
 * High-level texture abstraction combining VkImage, VkImageView, and VkSampler.
 * Supports automatic mipmap generation and common texture formats.
 * 
 * Example usage:
 * ```java
 * VkTexture texture = VkTexture.builder()
 *     .device(device)
 *     .allocator(allocator)
 *     .size(512, 512)
 *     .format(VkFormat.VK_FORMAT_R8G8B8A8_UNORM)
 *     .generateMipmaps()
 *     .linear()
 *     .repeat()
 *     .build(arena);
 * 
 * // Upload data and generate mipmaps
 * texture.copyFromBuffer(commandBuffer, stagingBuffer, 0);
 * texture.generateMipmaps(commandBuffer);
 * ```
 */
public class VkTexture implements AutoCloseable {
    private final VkImage image;
    private final MemorySegment imageView;
    private final MemorySegment sampler;
    private final VkAllocation allocation;
    private final VkMemoryAllocator allocator;
    private final MemorySegment device;
    private final int width, height, depth;
    private final int mipLevels;
    private final int format;
    
    private VkTexture(VkImage image, MemorySegment imageView, MemorySegment sampler, 
                     VkAllocation allocation, VkMemoryAllocator allocator, MemorySegment device,
                     int width, int height, int depth, int mipLevels, int format) {
        this.image = image;
        this.imageView = imageView;
        this.sampler = sampler;
        this.allocation = allocation;
        this.allocator = allocator;
        this.device = device;
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.mipLevels = mipLevels;
        this.format = format;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    /** @return the VkImage handle */
    public MemorySegment image() { return image.handle(); }
    
    /** @return the VkImageView handle */
    public MemorySegment imageView() { return imageView; }
    
    /** @return the VkSampler handle */
    public MemorySegment sampler() { return sampler; }
    
    /** @return texture width */
    public int width() { return width; }
    
    /** @return texture height */
    public int height() { return height; }
    
    /** @return texture depth */
    public int depth() { return depth; }
    
    /** @return number of mip levels */
    public int mipLevels() { return mipLevels; }
    
    /** @return texture format */
    public int format() { return format; }
    
    /**
     * Transitions the image layout using a pipeline barrier.
     */
    public void transitionLayout(MemorySegment commandBuffer, int oldLayout, int newLayout, 
                                int srcStageMask, int dstStageMask, int srcAccessMask, int dstAccessMask) {
        MemorySegment barrier = VkImageMemoryBarrier.allocate(Arena.ofAuto());
        VkImageMemoryBarrier.sType(barrier, VkStructureType.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER);
        VkImageMemoryBarrier.oldLayout(barrier, oldLayout);
        VkImageMemoryBarrier.newLayout(barrier, newLayout);
        VkImageMemoryBarrier.srcQueueFamilyIndex(barrier, VkQueueFamily.VK_QUEUE_FAMILY_IGNORED);
        VkImageMemoryBarrier.dstQueueFamilyIndex(barrier, VkQueueFamily.VK_QUEUE_FAMILY_IGNORED);
        VkImageMemoryBarrier.image(barrier, image.handle());
        VkImageMemoryBarrier.srcAccessMask(barrier, srcAccessMask);
        VkImageMemoryBarrier.dstAccessMask(barrier, dstAccessMask);
        
        MemorySegment subresourceRange = VkImageMemoryBarrier.subresourceRange(barrier);
        VkImageSubresourceRange.aspectMask(subresourceRange, VkImageAspectFlagBits.VK_IMAGE_ASPECT_COLOR_BIT);
        VkImageSubresourceRange.baseMipLevel(subresourceRange, 0);
        VkImageSubresourceRange.levelCount(subresourceRange, mipLevels);
        VkImageSubresourceRange.baseArrayLayer(subresourceRange, 0);
        VkImageSubresourceRange.layerCount(subresourceRange, 1);
        
        VulkanExtensions.cmdPipelineBarrier(commandBuffer, srcStageMask, dstStageMask, 0,
            0, MemorySegment.NULL, 0, MemorySegment.NULL, 1, barrier);
    }
    
    /**
     * Copies data from a buffer to this texture.
     */
    public void copyFromBuffer(MemorySegment commandBuffer, MemorySegment buffer, int mipLevel) {
        MemorySegment region = VkBufferImageCopy.allocate(Arena.ofAuto());
        VkBufferImageCopy.bufferOffset(region, 0);
        VkBufferImageCopy.bufferRowLength(region, 0);
        VkBufferImageCopy.bufferImageHeight(region, 0);
        
        MemorySegment imageSubresource = VkBufferImageCopy.imageSubresource(region);
        VkImageSubresourceLayers.aspectMask(imageSubresource, VkImageAspectFlagBits.VK_IMAGE_ASPECT_COLOR_BIT);
        VkImageSubresourceLayers.mipLevel(imageSubresource, mipLevel);
        VkImageSubresourceLayers.baseArrayLayer(imageSubresource, 0);
        VkImageSubresourceLayers.layerCount(imageSubresource, 1);
        
        MemorySegment imageOffset = VkBufferImageCopy.imageOffset(region);
        VkOffset3D.x(imageOffset, 0);
        VkOffset3D.y(imageOffset, 0);
        VkOffset3D.z(imageOffset, 0);
        
        MemorySegment imageExtent = VkBufferImageCopy.imageExtent(region);
        VkExtent3D.width(imageExtent, Math.max(1, width >> mipLevel));
        VkExtent3D.height(imageExtent, Math.max(1, height >> mipLevel));
        VkExtent3D.depth(imageExtent, Math.max(1, depth >> mipLevel));
        
        VulkanExtensions.cmdCopyBufferToImage(commandBuffer, buffer, image.handle(),
            VkImageLayout.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, region);
    }
    
    /**
     * Generates mipmaps for this texture using blit operations.
     */
    public void generateMipmaps(MemorySegment commandBuffer) {
        if (mipLevels <= 1) return;
        
        for (int i = 1; i < mipLevels; i++) {
            // Transition previous mip level to transfer src
            transitionMipLevel(commandBuffer, i - 1, 
                VkImageLayout.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                VkImageLayout.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                VkPipelineStageFlagBits.VK_PIPELINE_STAGE_TRANSFER_BIT,
                VkPipelineStageFlagBits.VK_PIPELINE_STAGE_TRANSFER_BIT,
                VkAccessFlagBits.VK_ACCESS_TRANSFER_WRITE_BIT,
                VkAccessFlagBits.VK_ACCESS_TRANSFER_READ_BIT);
            
            // Blit from previous mip level to current
            blitMipLevel(commandBuffer, i - 1, i);
            
            // Transition previous mip level to shader read
            transitionMipLevel(commandBuffer, i - 1,
                VkImageLayout.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                VkImageLayout.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                VkPipelineStageFlagBits.VK_PIPELINE_STAGE_TRANSFER_BIT,
                VkPipelineStageFlagBits.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                VkAccessFlagBits.VK_ACCESS_TRANSFER_READ_BIT,
                VkAccessFlagBits.VK_ACCESS_SHADER_READ_BIT);
        }
        
        // Transition last mip level to shader read
        transitionMipLevel(commandBuffer, mipLevels - 1,
            VkImageLayout.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
            VkImageLayout.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
            VkPipelineStageFlagBits.VK_PIPELINE_STAGE_TRANSFER_BIT,
            VkPipelineStageFlagBits.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
            VkAccessFlagBits.VK_ACCESS_TRANSFER_WRITE_BIT,
            VkAccessFlagBits.VK_ACCESS_SHADER_READ_BIT);
    }
    
    private void transitionMipLevel(MemorySegment commandBuffer, int mipLevel, int oldLayout, int newLayout,
                                   int srcStageMask, int dstStageMask, int srcAccessMask, int dstAccessMask) {
        MemorySegment barrier = VkImageMemoryBarrier.allocate(Arena.ofAuto());
        VkImageMemoryBarrier.sType(barrier, VkStructureType.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER);
        VkImageMemoryBarrier.oldLayout(barrier, oldLayout);
        VkImageMemoryBarrier.newLayout(barrier, newLayout);
        VkImageMemoryBarrier.srcQueueFamilyIndex(barrier, VkQueueFamily.VK_QUEUE_FAMILY_IGNORED);
        VkImageMemoryBarrier.dstQueueFamilyIndex(barrier, VkQueueFamily.VK_QUEUE_FAMILY_IGNORED);
        VkImageMemoryBarrier.image(barrier, image.handle());
        VkImageMemoryBarrier.srcAccessMask(barrier, srcAccessMask);
        VkImageMemoryBarrier.dstAccessMask(barrier, dstAccessMask);
        
        MemorySegment subresourceRange = VkImageMemoryBarrier.subresourceRange(barrier);
        VkImageSubresourceRange.aspectMask(subresourceRange, VkImageAspectFlagBits.VK_IMAGE_ASPECT_COLOR_BIT);
        VkImageSubresourceRange.baseMipLevel(subresourceRange, mipLevel);
        VkImageSubresourceRange.levelCount(subresourceRange, 1);
        VkImageSubresourceRange.baseArrayLayer(subresourceRange, 0);
        VkImageSubresourceRange.layerCount(subresourceRange, 1);
        
        VulkanExtensions.cmdPipelineBarrier(commandBuffer, srcStageMask, dstStageMask, 0,
            0, MemorySegment.NULL, 0, MemorySegment.NULL, 1, barrier);
    }
    
    private void blitMipLevel(MemorySegment commandBuffer, int srcMip, int dstMip) {
        MemorySegment blit = VkImageBlit.allocate(Arena.ofAuto());
        
        MemorySegment srcSubresource = VkImageBlit.srcSubresource(blit);
        VkImageSubresourceLayers.aspectMask(srcSubresource, VkImageAspectFlagBits.VK_IMAGE_ASPECT_COLOR_BIT);
        VkImageSubresourceLayers.mipLevel(srcSubresource, srcMip);
        VkImageSubresourceLayers.baseArrayLayer(srcSubresource, 0);
        VkImageSubresourceLayers.layerCount(srcSubresource, 1);
        
        MemorySegment srcOffsets = VkImageBlit.srcOffsets(blit);
        VkOffset3D.x(srcOffsets.asSlice(0, VkOffset3D.layout().byteSize()), 0);
        VkOffset3D.y(srcOffsets.asSlice(0, VkOffset3D.layout().byteSize()), 0);
        VkOffset3D.z(srcOffsets.asSlice(0, VkOffset3D.layout().byteSize()), 0);
        VkOffset3D.x(srcOffsets.asSlice(VkOffset3D.layout().byteSize(), VkOffset3D.layout().byteSize()), Math.max(1, width >> srcMip));
        VkOffset3D.y(srcOffsets.asSlice(VkOffset3D.layout().byteSize(), VkOffset3D.layout().byteSize()), Math.max(1, height >> srcMip));
        VkOffset3D.z(srcOffsets.asSlice(VkOffset3D.layout().byteSize(), VkOffset3D.layout().byteSize()), Math.max(1, depth >> srcMip));
        
        MemorySegment dstSubresource = VkImageBlit.dstSubresource(blit);
        VkImageSubresourceLayers.aspectMask(dstSubresource, VkImageAspectFlagBits.VK_IMAGE_ASPECT_COLOR_BIT);
        VkImageSubresourceLayers.mipLevel(dstSubresource, dstMip);
        VkImageSubresourceLayers.baseArrayLayer(dstSubresource, 0);
        VkImageSubresourceLayers.layerCount(dstSubresource, 1);
        
        MemorySegment dstOffsets = VkImageBlit.dstOffsets(blit);
        VkOffset3D.x(dstOffsets.asSlice(0, VkOffset3D.layout().byteSize()), 0);
        VkOffset3D.y(dstOffsets.asSlice(0, VkOffset3D.layout().byteSize()), 0);
        VkOffset3D.z(dstOffsets.asSlice(0, VkOffset3D.layout().byteSize()), 0);
        VkOffset3D.x(dstOffsets.asSlice(VkOffset3D.layout().byteSize(), VkOffset3D.layout().byteSize()), Math.max(1, width >> dstMip));
        VkOffset3D.y(dstOffsets.asSlice(VkOffset3D.layout().byteSize(), VkOffset3D.layout().byteSize()), Math.max(1, height >> dstMip));
        VkOffset3D.z(dstOffsets.asSlice(VkOffset3D.layout().byteSize(), VkOffset3D.layout().byteSize()), Math.max(1, depth >> dstMip));
        
        VulkanExtensions.cmdBlitImage(commandBuffer, image.handle(), VkImageLayout.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
            image.handle(), VkImageLayout.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, blit, VkFilter.VK_FILTER_LINEAR);
    }
    
    @Override
    public void close() {
        if (sampler != null && !sampler.equals(MemorySegment.NULL)) {
            VulkanExtensions.destroySampler(device, sampler);
        }
        if (imageView != null && !imageView.equals(MemorySegment.NULL)) {
            VulkanExtensions.destroyImageView(device, imageView);
        }
        if (image != null) {
            image.close();
        }
    }
    
    public static class Builder {
        private MemorySegment device;
        private VkMemoryAllocator allocator;
        private int width = 1, height = 1, depth = 1;
        private int format = VkFormat.VK_FORMAT_R8G8B8A8_UNORM;
        private int imageType = VkImageType.VK_IMAGE_TYPE_2D;
        private int usage = VkImageUsageFlagBits.VK_IMAGE_USAGE_SAMPLED_BIT;
        private int mipLevels = 1;
        private int arrayLayers = 1;
        private int samples = VkSampleCountFlagBits.VK_SAMPLE_COUNT_1_BIT;
        private boolean generateMipmaps = false;
        
        // Sampler properties
        private int magFilter = VkFilter.VK_FILTER_LINEAR;
        private int minFilter = VkFilter.VK_FILTER_LINEAR;
        private int mipmapMode = VkSamplerMipmapMode.VK_SAMPLER_MIPMAP_MODE_LINEAR;
        private int addressModeU = VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_REPEAT;
        private int addressModeV = VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_REPEAT;
        private int addressModeW = VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_REPEAT;
        private float maxAnisotropy = 1.0f;
        private boolean anisotropyEnable = false;
        
        public Builder device(MemorySegment device) {
            this.device = device;
            return this;
        }
        
        public Builder allocator(VkMemoryAllocator allocator) {
            this.allocator = allocator;
            return this;
        }
        
        public Builder size(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }
        
        public Builder size(int width, int height, int depth) {
            this.width = width;
            this.height = height;
            this.depth = depth;
            this.imageType = VkImageType.VK_IMAGE_TYPE_3D;
            return this;
        }
        
        public Builder format(int format) {
            this.format = format;
            return this;
        }
        
        public Builder usage(int usage) {
            this.usage = usage;
            return this;
        }
        
        public Builder mipLevels(int levels) {
            this.mipLevels = levels;
            return this;
        }
        
        public Builder generateMipmaps() {
            this.generateMipmaps = true;
            this.mipLevels = calculateMipLevels(width, height);
            this.usage |= VkImageUsageFlagBits.VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VkImageUsageFlagBits.VK_IMAGE_USAGE_TRANSFER_DST_BIT;
            return this;
        }
        
        public Builder arrayLayers(int layers) {
            this.arrayLayers = layers;
            return this;
        }
        
        public Builder samples(int samples) {
            this.samples = samples;
            return this;
        }
        
        public Builder linear() {
            this.magFilter = VkFilter.VK_FILTER_LINEAR;
            this.minFilter = VkFilter.VK_FILTER_LINEAR;
            return this;
        }
        
        public Builder nearest() {
            this.magFilter = VkFilter.VK_FILTER_NEAREST;
            this.minFilter = VkFilter.VK_FILTER_NEAREST;
            return this;
        }
        
        public Builder repeat() {
            this.addressModeU = VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_REPEAT;
            this.addressModeV = VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_REPEAT;
            this.addressModeW = VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_REPEAT;
            return this;
        }
        
        public Builder clampToEdge() {
            this.addressModeU = VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
            this.addressModeV = VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
            this.addressModeW = VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
            return this;
        }
        
        public Builder anisotropy(float maxAnisotropy) {
            this.anisotropyEnable = true;
            this.maxAnisotropy = maxAnisotropy;
            return this;
        }
        
        private int calculateMipLevels(int width, int height) {
            return (int)Math.floor(Math.log(Math.max(width, height)) / Math.log(2)) + 1;
        }
        
        private boolean isDepthFormat(int format) {
            return format == VkFormat.VK_FORMAT_D16_UNORM ||
                   format == VkFormat.VK_FORMAT_D32_SFLOAT ||
                   format == VkFormat.VK_FORMAT_D24_UNORM_S8_UINT ||
                   format == VkFormat.VK_FORMAT_D32_SFLOAT_S8_UINT;
        }
        
        public VkTexture build(Arena arena) {
            if (device == null) throw new IllegalStateException("device not set");
            if (allocator == null) throw new IllegalStateException("allocator not set");
            
            // Create image
            VkImage image = VkImage.builder()
                .device(device)
                .dimensions(width, height, depth)
                .format(format)
                .mipLevels(mipLevels)
                .arrayLayers(arrayLayers)
                .samples(samples)
                .usage(usage)
                .build(arena);
            
            // VkImage already handles memory allocation and binding, so no need to do it again
            
            // Create image view
            MemorySegment imageViewInfo = VkImageViewCreateInfo.allocate(arena);
            VkImageViewCreateInfo.sType(imageViewInfo, VkStructureType.VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO);
            VkImageViewCreateInfo.image(imageViewInfo, image.handle());
            VkImageViewCreateInfo.viewType(imageViewInfo, imageType == VkImageType.VK_IMAGE_TYPE_3D ? 
                VkImageViewType.VK_IMAGE_VIEW_TYPE_3D : VkImageViewType.VK_IMAGE_VIEW_TYPE_2D);
            VkImageViewCreateInfo.format(imageViewInfo, format);
            
            MemorySegment subresourceRange = VkImageViewCreateInfo.subresourceRange(imageViewInfo);
            // Use correct aspect mask based on format
            int aspectMask = isDepthFormat(format) ? VkImageAspectFlagBits.VK_IMAGE_ASPECT_DEPTH_BIT : VkImageAspectFlagBits.VK_IMAGE_ASPECT_COLOR_BIT;
            VkImageSubresourceRange.aspectMask(subresourceRange, aspectMask);
            VkImageSubresourceRange.baseMipLevel(subresourceRange, 0);
            VkImageSubresourceRange.levelCount(subresourceRange, mipLevels);
            VkImageSubresourceRange.baseArrayLayer(subresourceRange, 0);
            VkImageSubresourceRange.layerCount(subresourceRange, arrayLayers);
            
            MemorySegment imageViewPtr = arena.allocate(ValueLayout.ADDRESS);
            VulkanExtensions.createImageView(device, imageViewInfo, imageViewPtr).check();
            MemorySegment imageView = imageViewPtr.get(ValueLayout.ADDRESS, 0);
            
            // Create sampler
            MemorySegment samplerInfo = VkSamplerCreateInfo.allocate(arena);
            VkSamplerCreateInfo.sType(samplerInfo, VkStructureType.VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO);
            VkSamplerCreateInfo.magFilter(samplerInfo, magFilter);
            VkSamplerCreateInfo.minFilter(samplerInfo, minFilter);
            VkSamplerCreateInfo.mipmapMode(samplerInfo, mipmapMode);
            VkSamplerCreateInfo.addressModeU(samplerInfo, addressModeU);
            VkSamplerCreateInfo.addressModeV(samplerInfo, addressModeV);
            VkSamplerCreateInfo.addressModeW(samplerInfo, addressModeW);
            VkSamplerCreateInfo.anisotropyEnable(samplerInfo, anisotropyEnable ? 1 : 0);
            VkSamplerCreateInfo.maxAnisotropy(samplerInfo, maxAnisotropy);
            VkSamplerCreateInfo.minLod(samplerInfo, 0.0f);
            VkSamplerCreateInfo.maxLod(samplerInfo, mipLevels);
            
            MemorySegment samplerPtr = arena.allocate(ValueLayout.ADDRESS);
            VulkanExtensions.createSampler(device, samplerInfo, samplerPtr).check();
            MemorySegment sampler = samplerPtr.get(ValueLayout.ADDRESS, 0);
            
            return new VkTexture(image, imageView, sampler, null, null, device, width, height, depth, mipLevels, format);
        }
    }
}