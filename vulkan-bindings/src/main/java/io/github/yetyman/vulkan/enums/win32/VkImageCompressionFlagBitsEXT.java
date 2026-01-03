package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkImageCompressionFlagBitsEXT
 * Generated from jextract bindings
 */
public record VkImageCompressionFlagBitsEXT(int value) {

    public static final VkImageCompressionFlagBitsEXT VK_IMAGE_COMPRESSION_DEFAULT_EXT = new VkImageCompressionFlagBitsEXT(0);
    public static final VkImageCompressionFlagBitsEXT VK_IMAGE_COMPRESSION_DISABLED_EXT = new VkImageCompressionFlagBitsEXT(4);
    public static final VkImageCompressionFlagBitsEXT VK_IMAGE_COMPRESSION_FIXED_RATE_DEFAULT_EXT = new VkImageCompressionFlagBitsEXT(1);
    public static final VkImageCompressionFlagBitsEXT VK_IMAGE_COMPRESSION_FIXED_RATE_EXPLICIT_EXT = new VkImageCompressionFlagBitsEXT(2);
    public static final VkImageCompressionFlagBitsEXT VK_IMAGE_COMPRESSION_FLAG_BITS_MAX_ENUM_EXT = new VkImageCompressionFlagBitsEXT(2147483647);

    public static VkImageCompressionFlagBitsEXT fromValue(int value) {
        return switch (value) {
            case 0 -> VK_IMAGE_COMPRESSION_DEFAULT_EXT;
            case 4 -> VK_IMAGE_COMPRESSION_DISABLED_EXT;
            case 1 -> VK_IMAGE_COMPRESSION_FIXED_RATE_DEFAULT_EXT;
            case 2 -> VK_IMAGE_COMPRESSION_FIXED_RATE_EXPLICIT_EXT;
            case 2147483647 -> VK_IMAGE_COMPRESSION_FLAG_BITS_MAX_ENUM_EXT;
            default -> new VkImageCompressionFlagBitsEXT(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
