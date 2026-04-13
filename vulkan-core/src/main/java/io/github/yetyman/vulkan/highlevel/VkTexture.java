package io.github.yetyman.vulkan.highlevel;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.command.VkCopy;
import io.github.yetyman.vulkan.command.VkBlit;
import io.github.yetyman.vulkan.command.VkBarrierCmd;
import io.github.yetyman.vulkan.generated.VulkanFFM;
import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;

import java.lang.foreign.*;

/**
 * High-level texture abstraction combining VkImage, VkImageView, and VkSampler.
 * Supports automatic mipmap generation and common texture formats.
 * <p>
 * Example usage:
 * ```java
 * VkTexture texture = VkTexture.builder()
 * .device(device)
 * .allocator(allocator)
 * .size(512, 512)
 * .format(VkFormat.VK_FORMAT_R8G8B8A8_UNORM.value())
 * .generateMipmaps()
 * .linear()
 * .repeat()
 * .build(arena);
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
    private final VkDevice device;
    private final int width, height, depth;
    private final int mipLevels;
    private final int format;

    private VkTexture(VkImage image, MemorySegment imageView, MemorySegment sampler, VkDevice device,
                      int width, int height, int depth, int mipLevels, int format) {
        this.image = image;
        this.imageView = imageView;
        this.sampler = sampler;
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

    /**
     * @return the VkImage handle
     */
    public MemorySegment image() {
        return image.handle();
    }

    /**
     * @return the VkImageView handle
     */
    public MemorySegment imageView() {
        return imageView;
    }

    /**
     * @return the VkSampler handle
     */
    public MemorySegment sampler() {
        return sampler;
    }

    /**
     * @return texture width
     */
    public int width() {
        return width;
    }

    /**
     * @return texture height
     */
    public int height() {
        return height;
    }

    /**
     * @return texture depth
     */
    public int depth() {
        return depth;
    }

    /**
     * @return number of mip levels
     */
    public int mipLevels() {
        return mipLevels;
    }

    /**
     * @return texture format
     */
    public int format() {
        return format;
    }

    /**
     * Transitions the image layout using a pipeline barrier.
     */
    public void transitionLayout(MemorySegment commandBuffer, int oldLayout, int newLayout,
                                 int srcStageMask, int dstStageMask, int srcAccessMask, int dstAccessMask) {
        MemorySegment barrier = VkImageMemoryBarrier.allocate(Arena.ofAuto());
        VkImageMemoryBarrier.sType(barrier, VkStructureType.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER.value());
        VkImageMemoryBarrier.oldLayout(barrier, oldLayout);
        VkImageMemoryBarrier.newLayout(barrier, newLayout);
        VkImageMemoryBarrier.srcQueueFamilyIndex(barrier, VkQueueFamily.VK_QUEUE_FAMILY_IGNORED);
        VkImageMemoryBarrier.dstQueueFamilyIndex(barrier, VkQueueFamily.VK_QUEUE_FAMILY_IGNORED);
        VkImageMemoryBarrier.image(barrier, image.handle());
        VkImageMemoryBarrier.srcAccessMask(barrier, srcAccessMask);
        VkImageMemoryBarrier.dstAccessMask(barrier, dstAccessMask);

        MemorySegment subresourceRange = VkImageMemoryBarrier.subresourceRange(barrier);
        VkImageSubresourceRange.aspectMask(subresourceRange, VkImageAspectFlagBits.VK_IMAGE_ASPECT_COLOR_BIT.value());
        VkImageSubresourceRange.baseMipLevel(subresourceRange, 0);
        VkImageSubresourceRange.levelCount(subresourceRange, mipLevels);
        VkImageSubresourceRange.baseArrayLayer(subresourceRange, 0);
        VkImageSubresourceRange.layerCount(subresourceRange, 1);

        VkBarrierCmd.pipelineBarrier(commandBuffer, srcStageMask, dstStageMask, 0,
                0, MemorySegment.NULL, 0, MemorySegment.NULL, 1, barrier);
    }

    /**
     * Copies data from a buffer to this texture.
     */
    public void copyFromBuffer(MemorySegment commandBuffer, MemorySegment buffer, int mipLevel) {
        int mipWidth = Math.max(1, width >> mipLevel);
        int mipHeight = Math.max(1, height >> mipLevel);
        int mipDepth = Math.max(1, depth >> mipLevel);
        VkCopy.copyBufferToImage(commandBuffer, buffer, image.handle(), VkImageLayout.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL.value(), mipWidth, mipHeight, mipDepth);
    }

    /**
     * Generates mipmaps for this texture using blit operations.
     */
    public void generateMipmaps(MemorySegment commandBuffer) {
        if (mipLevels <= 1) return;

        for (int i = 1; i < mipLevels; i++) {
            // Transition previous mip level to transfer src
            transitionMipLevel(commandBuffer, i - 1,
                    VkImageLayout.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL.value(),
                    VkImageLayout.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL.value(),
                    VkPipelineStageFlagBits.VK_PIPELINE_STAGE_TRANSFER_BIT.value(),
                    VkPipelineStageFlagBits.VK_PIPELINE_STAGE_TRANSFER_BIT.value(),
                    VkAccessFlagBits.VK_ACCESS_TRANSFER_WRITE_BIT.value(),
                    VkAccessFlagBits.VK_ACCESS_TRANSFER_READ_BIT.value());

            // Blit from previous mip level to current
            blitMipLevel(commandBuffer, i - 1, i);

            // Transition previous mip level to shader read
            transitionMipLevel(commandBuffer, i - 1,
                    VkImageLayout.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL.value(),
                    VkImageLayout.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL.value(),
                    VkPipelineStageFlagBits.VK_PIPELINE_STAGE_TRANSFER_BIT.value(),
                    VkPipelineStageFlagBits.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT.value(),
                    VkAccessFlagBits.VK_ACCESS_TRANSFER_READ_BIT.value(),
                    VkAccessFlagBits.VK_ACCESS_SHADER_READ_BIT.value());
        }

        // Transition last mip level to shader read
        transitionMipLevel(commandBuffer, mipLevels - 1,
                VkImageLayout.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL.value(),
                VkImageLayout.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL.value(),
                VkPipelineStageFlagBits.VK_PIPELINE_STAGE_TRANSFER_BIT.value(),
                VkPipelineStageFlagBits.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT.value(),
                VkAccessFlagBits.VK_ACCESS_TRANSFER_WRITE_BIT.value(),
                VkAccessFlagBits.VK_ACCESS_SHADER_READ_BIT.value());
    }

    private void transitionMipLevel(MemorySegment commandBuffer, int mipLevel, int oldLayout, int newLayout,
                                    int srcStageMask, int dstStageMask, int srcAccessMask, int dstAccessMask) {
        MemorySegment barrier = VkImageMemoryBarrier.allocate(Arena.ofAuto());
        VkImageMemoryBarrier.sType(barrier, VkStructureType.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER.value());
        VkImageMemoryBarrier.oldLayout(barrier, oldLayout);
        VkImageMemoryBarrier.newLayout(barrier, newLayout);
        VkImageMemoryBarrier.srcQueueFamilyIndex(barrier, VkQueueFamily.VK_QUEUE_FAMILY_IGNORED);
        VkImageMemoryBarrier.dstQueueFamilyIndex(barrier, VkQueueFamily.VK_QUEUE_FAMILY_IGNORED);
        VkImageMemoryBarrier.image(barrier, image.handle());
        VkImageMemoryBarrier.srcAccessMask(barrier, srcAccessMask);
        VkImageMemoryBarrier.dstAccessMask(barrier, dstAccessMask);

        MemorySegment subresourceRange = VkImageMemoryBarrier.subresourceRange(barrier);
        VkImageSubresourceRange.aspectMask(subresourceRange, VkImageAspectFlagBits.VK_IMAGE_ASPECT_COLOR_BIT.value());
        VkImageSubresourceRange.baseMipLevel(subresourceRange, mipLevel);
        VkImageSubresourceRange.levelCount(subresourceRange, 1);
        VkImageSubresourceRange.baseArrayLayer(subresourceRange, 0);
        VkImageSubresourceRange.layerCount(subresourceRange, 1);

        VkBarrierCmd.pipelineBarrier(commandBuffer, srcStageMask, dstStageMask, 0,
                0, MemorySegment.NULL, 0, MemorySegment.NULL, 1, barrier);
    }

    private void blitMipLevel(MemorySegment commandBuffer, int srcMip, int dstMip) {
        int mipWidth = Math.max(1, width >> srcMip);
        int mipHeight = Math.max(1, height >> srcMip);
        int nextMipWidth = Math.max(1, width >> dstMip);
        int nextMipHeight = Math.max(1, height >> dstMip);
        VkBlit.blitImage(commandBuffer, image.handle(), VkImageLayout.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL.value(),
                image.handle(), VkImageLayout.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL.value(),
                mipWidth, mipHeight, nextMipWidth, nextMipHeight, VkFilter.VK_FILTER_LINEAR.value());
    }

    @Override
    public void close() {
        if (sampler != null && !sampler.equals(MemorySegment.NULL)) {
            Vulkan.destroySampler(device.handle(), sampler);
        }
        if (imageView != null && !imageView.equals(MemorySegment.NULL)) {
            Vulkan.destroyImageView(device.handle(), imageView);
        }
        if (image != null) {
            image.close();
        }
    }

    public static class Builder {
        private VkDevice device;
        private PoolAllocator allocator;
        private int width = 1, height = 1, depth = 1;
        private int format = VkFormat.VK_FORMAT_R8G8B8A8_UNORM.value();
        private int imageType = VkImageType.VK_IMAGE_TYPE_2D.value();
        private int usage = VkImageUsageFlagBits.VK_IMAGE_USAGE_SAMPLED_BIT.value();
        private int mipLevels = 1;
        private int arrayLayers = 1;
        private int samples = VkSampleCountFlagBits.VK_SAMPLE_COUNT_1_BIT.value();
        private boolean generateMipmaps = false;

        // Sampler properties
        private int magFilter = VkFilter.VK_FILTER_LINEAR.value();
        private int minFilter = VkFilter.VK_FILTER_LINEAR.value();
        private int mipmapMode = VkSamplerMipmapMode.VK_SAMPLER_MIPMAP_MODE_LINEAR.value();
        private int addressModeU = VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_REPEAT.value();
        private int addressModeV = VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_REPEAT.value();
        private int addressModeW = VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_REPEAT.value();
        private float maxAnisotropy = 1.0f;
        private boolean anisotropyEnable = false;

        public Builder device(VkDevice device) {
            this.device = device;
            return this;
        }

        public Builder allocator(PoolAllocator allocator) {
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
            this.imageType = VkImageType.VK_IMAGE_TYPE_3D.value();
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
            this.usage |= VkImageUsageFlagBits.VK_IMAGE_USAGE_TRANSFER_SRC_BIT.value() | VkImageUsageFlagBits.VK_IMAGE_USAGE_TRANSFER_DST_BIT.value();
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
            this.magFilter = VkFilter.VK_FILTER_LINEAR.value();
            this.minFilter = VkFilter.VK_FILTER_LINEAR.value();
            return this;
        }

        public Builder nearest() {
            this.magFilter = VkFilter.VK_FILTER_NEAREST.value();
            this.minFilter = VkFilter.VK_FILTER_NEAREST.value();
            return this;
        }

        public Builder repeat() {
            this.addressModeU = VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_REPEAT.value();
            this.addressModeV = VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_REPEAT.value();
            this.addressModeW = VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_REPEAT.value();
            return this;
        }

        public Builder clampToEdge() {
            this.addressModeU = VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE.value();
            this.addressModeV = VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE.value();
            this.addressModeW = VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE.value();
            return this;
        }

        public Builder anisotropy(float maxAnisotropy) {
            this.anisotropyEnable = true;
            this.maxAnisotropy = maxAnisotropy;
            return this;
        }

        private int calculateMipLevels(int width, int height) {
            return (int) Math.floor(Math.log(Math.max(width, height)) / Math.log(2)) + 1;
        }

        private boolean isDepthFormat(int format) {
            return format == VkFormat.VK_FORMAT_D16_UNORM.value() ||
                    format == VkFormat.VK_FORMAT_D32_SFLOAT.value() ||
                    format == VkFormat.VK_FORMAT_D24_UNORM_S8_UINT.value() ||
                    format == VkFormat.VK_FORMAT_D32_SFLOAT_S8_UINT.value();
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
            VkImageViewCreateInfo.sType(imageViewInfo, VkStructureType.VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO.value());
            VkImageViewCreateInfo.image(imageViewInfo, image.handle());
            VkImageViewCreateInfo.viewType(imageViewInfo, imageType == VkImageType.VK_IMAGE_TYPE_3D.value() ?
                    VkImageViewType.VK_IMAGE_VIEW_TYPE_3D.value() : VkImageViewType.VK_IMAGE_VIEW_TYPE_2D.value());
            VkImageViewCreateInfo.format(imageViewInfo, format);

            MemorySegment subresourceRange = VkImageViewCreateInfo.subresourceRange(imageViewInfo);
            // Use correct aspect mask based on format
            int aspectMask = isDepthFormat(format) ? VkImageAspectFlagBits.VK_IMAGE_ASPECT_DEPTH_BIT.value() : VkImageAspectFlagBits.VK_IMAGE_ASPECT_COLOR_BIT.value();
            VkImageSubresourceRange.aspectMask(subresourceRange, aspectMask);
            VkImageSubresourceRange.baseMipLevel(subresourceRange, 0);
            VkImageSubresourceRange.levelCount(subresourceRange, mipLevels);
            VkImageSubresourceRange.baseArrayLayer(subresourceRange, 0);
            VkImageSubresourceRange.layerCount(subresourceRange, arrayLayers);

            MemorySegment imageViewPtr = arena.allocate(ValueLayout.ADDRESS);
            Vulkan.createImageView(device.handle(), imageViewInfo, imageViewPtr).check();
            MemorySegment imageView = imageViewPtr.get(ValueLayout.ADDRESS, 0);

            // Create sampler
            MemorySegment samplerInfo = VkSamplerCreateInfo.allocate(arena);
            VkSamplerCreateInfo.sType(samplerInfo, VkStructureType.VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO.value());
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
            Vulkan.createSampler(device.handle(), samplerInfo, samplerPtr).check();
            MemorySegment sampler = samplerPtr.get(ValueLayout.ADDRESS, 0);

            return new VkTexture(image, imageView, sampler, device, width, height, depth, mipLevels, format);
        }
    }
}