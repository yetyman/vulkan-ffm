package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkImageTiling
 * Generated from jextract bindings
 */
public record VkImageTiling(int value) {

    public static final VkImageTiling VK_IMAGE_TILING_DRM_FORMAT_MODIFIER_EXT = new VkImageTiling(1000158000);
    public static final VkImageTiling VK_IMAGE_TILING_LINEAR = new VkImageTiling(1);
    public static final VkImageTiling VK_IMAGE_TILING_MAX_ENUM = new VkImageTiling(2147483647);
    public static final VkImageTiling VK_IMAGE_TILING_OPTIMAL = new VkImageTiling(0);

    public static VkImageTiling fromValue(int value) {
        return switch (value) {
            case 1000158000 -> VK_IMAGE_TILING_DRM_FORMAT_MODIFIER_EXT;
            case 1 -> VK_IMAGE_TILING_LINEAR;
            case 2147483647 -> VK_IMAGE_TILING_MAX_ENUM;
            case 0 -> VK_IMAGE_TILING_OPTIMAL;
            default -> new VkImageTiling(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
