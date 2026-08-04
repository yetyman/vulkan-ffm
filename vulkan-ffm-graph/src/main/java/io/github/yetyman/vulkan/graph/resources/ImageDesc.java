package io.github.yetyman.vulkan.graph.resources;

import io.github.yetyman.vulkan.enums.VkFormat;
import io.github.yetyman.vulkan.enums.VkImageUsageFlagBits;
import io.github.yetyman.vulkan.enums.VkSampleCountFlagBits;

/**
 * Descriptor for a graph-managed image resource. Captures the parameters needed to
 * allocate the image without actually allocating it. The graph compiler uses these
 * to allocate (and alias) physical memory at compile time.
 */
public class ImageDesc {

    private final int width;
    private final int height;
    private final int depth;
    private final int format;
    private final int usage;
    private final int samples;
    private final int mipLevels;
    private final int arrayLayers;

    private ImageDesc(int width, int height, int depth, int format, int usage,
                      int samples, int mipLevels, int arrayLayers) {
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.format = format;
        this.usage = usage;
        this.samples = samples;
        this.mipLevels = mipLevels;
        this.arrayLayers = arrayLayers;
    }

    /** Color attachment with default usage (color attachment + sampled) */
    public static ImageDesc color(int width, int height, int format) {
        return new ImageDesc(width, height, 1, format,
            VkImageUsageFlagBits.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT.value()
                | VkImageUsageFlagBits.VK_IMAGE_USAGE_SAMPLED_BIT.value(),
            VkSampleCountFlagBits.VK_SAMPLE_COUNT_1_BIT.value(), 1, 1);
    }

    /** Color attachment with explicit sample count */
    public static ImageDesc color(int width, int height, int format, int samples) {
        return new ImageDesc(width, height, 1, format,
            VkImageUsageFlagBits.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT.value()
                | VkImageUsageFlagBits.VK_IMAGE_USAGE_SAMPLED_BIT.value(),
            samples, 1, 1);
    }

    /** Depth attachment */
    public static ImageDesc depth(int width, int height) {
        return depth(width, height, VkFormat.VK_FORMAT_D32_SFLOAT.value());
    }

    /** Depth attachment with explicit format */
    public static ImageDesc depth(int width, int height, int format) {
        return new ImageDesc(width, height, 1, format,
            VkImageUsageFlagBits.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT.value()
                | VkImageUsageFlagBits.VK_IMAGE_USAGE_SAMPLED_BIT.value(),
            VkSampleCountFlagBits.VK_SAMPLE_COUNT_1_BIT.value(), 1, 1);
    }

    /** Storage image (compute read/write) */
    public static ImageDesc storage(int width, int height, int format) {
        return new ImageDesc(width, height, 1, format,
            VkImageUsageFlagBits.VK_IMAGE_USAGE_STORAGE_BIT.value()
                | VkImageUsageFlagBits.VK_IMAGE_USAGE_SAMPLED_BIT.value(),
            VkSampleCountFlagBits.VK_SAMPLE_COUNT_1_BIT.value(), 1, 1);
    }

    /** Fully custom descriptor */
    public static ImageDesc custom(int width, int height, int depth, int format,
                                   int usage, int samples, int mipLevels, int arrayLayers) {
        return new ImageDesc(width, height, depth, format, usage, samples, mipLevels, arrayLayers);
    }

    /** @return a copy with new dimensions (for resize) */
    public ImageDesc withDimensions(int newWidth, int newHeight) {
        return new ImageDesc(newWidth, newHeight, depth, format, usage, samples, mipLevels, arrayLayers);
    }

    public int width() { return width; }
    public int height() { return height; }
    public int depth() { return depth; }
    public int format() { return format; }
    public int usage() { return usage; }
    public int samples() { return samples; }
    public int mipLevels() { return mipLevels; }
    public int arrayLayers() { return arrayLayers; }

    /**
     * Computes the minimum memory size needed for this image (conservative estimate).
     * Used by the aliaser to determine aliasing group heap sizes.
     */
    public long estimateMemorySize() {
        long pixelSize = estimateBytesPerPixel(format);
        long baseSize = (long) width * height * depth * arrayLayers * pixelSize * samples;
        // Account for mip chain (sum of geometric series: ~1.33x for full chain)
        if (mipLevels > 1) {
            baseSize = (baseSize * 4) / 3;
        }
        // Align to 256 bytes (common Vulkan alignment requirement)
        return (baseSize + 255) & ~255L;
    }

    private static long estimateBytesPerPixel(int format) {
        // Common formats
        if (format == VkFormat.VK_FORMAT_R8G8B8A8_UNORM.value() ||
            format == VkFormat.VK_FORMAT_R8G8B8A8_SRGB.value() ||
            format == VkFormat.VK_FORMAT_B8G8R8A8_UNORM.value() ||
            format == VkFormat.VK_FORMAT_B8G8R8A8_SRGB.value()) return 4;
        if (format == VkFormat.VK_FORMAT_R16G16B16A16_SFLOAT.value()) return 8;
        if (format == VkFormat.VK_FORMAT_R32G32B32A32_SFLOAT.value()) return 16;
        if (format == VkFormat.VK_FORMAT_D32_SFLOAT.value()) return 4;
        if (format == VkFormat.VK_FORMAT_D24_UNORM_S8_UINT.value()) return 4;
        if (format == VkFormat.VK_FORMAT_D32_SFLOAT_S8_UINT.value()) return 5;
        if (format == VkFormat.VK_FORMAT_R32_SFLOAT.value()) return 4;
        if (format == VkFormat.VK_FORMAT_R16_SFLOAT.value()) return 2;
        if (format == VkFormat.VK_FORMAT_R8_UNORM.value()) return 1;
        // Default conservative estimate
        return 4;
    }
}
