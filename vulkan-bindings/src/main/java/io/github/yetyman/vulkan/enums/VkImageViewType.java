package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkImageViewType
 * Generated from jextract bindings
 */
public record VkImageViewType(int value) {

    public static final VkImageViewType VK_IMAGE_VIEW_TYPE_1D = new VkImageViewType(0);
    public static final VkImageViewType VK_IMAGE_VIEW_TYPE_1D_ARRAY = new VkImageViewType(4);
    public static final VkImageViewType VK_IMAGE_VIEW_TYPE_2D = new VkImageViewType(1);
    public static final VkImageViewType VK_IMAGE_VIEW_TYPE_2D_ARRAY = new VkImageViewType(5);
    public static final VkImageViewType VK_IMAGE_VIEW_TYPE_3D = new VkImageViewType(2);
    public static final VkImageViewType VK_IMAGE_VIEW_TYPE_CUBE = new VkImageViewType(3);
    public static final VkImageViewType VK_IMAGE_VIEW_TYPE_CUBE_ARRAY = new VkImageViewType(6);
    public static final VkImageViewType VK_IMAGE_VIEW_TYPE_MAX_ENUM = new VkImageViewType(2147483647);

    public static VkImageViewType fromValue(int value) {
        return switch (value) {
            case 0 -> VK_IMAGE_VIEW_TYPE_1D;
            case 4 -> VK_IMAGE_VIEW_TYPE_1D_ARRAY;
            case 1 -> VK_IMAGE_VIEW_TYPE_2D;
            case 5 -> VK_IMAGE_VIEW_TYPE_2D_ARRAY;
            case 2 -> VK_IMAGE_VIEW_TYPE_3D;
            case 3 -> VK_IMAGE_VIEW_TYPE_CUBE;
            case 6 -> VK_IMAGE_VIEW_TYPE_CUBE_ARRAY;
            case 2147483647 -> VK_IMAGE_VIEW_TYPE_MAX_ENUM;
            default -> new VkImageViewType(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
