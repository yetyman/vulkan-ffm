package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkHostImageCopyFlagBits
 * Generated from jextract bindings
 */
public record VkHostImageCopyFlagBits(int value) {

    public static final VkHostImageCopyFlagBits VK_HOST_IMAGE_COPY_FLAG_BITS_MAX_ENUM = new VkHostImageCopyFlagBits(2147483647);
    public static final VkHostImageCopyFlagBits VK_HOST_IMAGE_COPY_MEMCPY = new VkHostImageCopyFlagBits(1);
    public static final VkHostImageCopyFlagBits VK_HOST_IMAGE_COPY_MEMCPY_BIT = VK_HOST_IMAGE_COPY_MEMCPY;
    public static final VkHostImageCopyFlagBits VK_HOST_IMAGE_COPY_MEMCPY_BIT_EXT = VK_HOST_IMAGE_COPY_MEMCPY;
    public static final VkHostImageCopyFlagBits VK_HOST_IMAGE_COPY_MEMCPY_EXT = VK_HOST_IMAGE_COPY_MEMCPY;

    public static VkHostImageCopyFlagBits fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_HOST_IMAGE_COPY_FLAG_BITS_MAX_ENUM;
            case 1 -> VK_HOST_IMAGE_COPY_MEMCPY;
            default -> new VkHostImageCopyFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
