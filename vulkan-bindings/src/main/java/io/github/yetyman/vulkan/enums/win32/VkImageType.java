package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkImageType
 * Generated from jextract bindings
 */
public record VkImageType(int value) {

    public static final VkImageType VK_IMAGE_TYPE_1D = new VkImageType(0);
    public static final VkImageType VK_IMAGE_TYPE_2D = new VkImageType(1);
    public static final VkImageType VK_IMAGE_TYPE_3D = new VkImageType(2);
    public static final VkImageType VK_IMAGE_TYPE_MAX_ENUM = new VkImageType(2147483647);

    public static VkImageType fromValue(int value) {
        return switch (value) {
            case 0 -> VK_IMAGE_TYPE_1D;
            case 1 -> VK_IMAGE_TYPE_2D;
            case 2 -> VK_IMAGE_TYPE_3D;
            case 2147483647 -> VK_IMAGE_TYPE_MAX_ENUM;
            default -> new VkImageType(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
