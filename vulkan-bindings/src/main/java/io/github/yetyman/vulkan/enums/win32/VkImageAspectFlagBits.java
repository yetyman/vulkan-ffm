package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkImageAspectFlagBits
 * Generated from jextract bindings
 */
public record VkImageAspectFlagBits(int value) {

    public static final VkImageAspectFlagBits VK_IMAGE_ASPECT_COLOR_BIT = new VkImageAspectFlagBits(1);
    public static final VkImageAspectFlagBits VK_IMAGE_ASPECT_DEPTH_BIT = new VkImageAspectFlagBits(2);
    public static final VkImageAspectFlagBits VK_IMAGE_ASPECT_FLAG_BITS_MAX_ENUM = new VkImageAspectFlagBits(2147483647);
    public static final VkImageAspectFlagBits VK_IMAGE_ASPECT_MEMORY_PLANE_0_BIT_EXT = new VkImageAspectFlagBits(128);
    public static final VkImageAspectFlagBits VK_IMAGE_ASPECT_MEMORY_PLANE_1_BIT_EXT = new VkImageAspectFlagBits(256);
    public static final VkImageAspectFlagBits VK_IMAGE_ASPECT_MEMORY_PLANE_2_BIT_EXT = new VkImageAspectFlagBits(512);
    public static final VkImageAspectFlagBits VK_IMAGE_ASPECT_MEMORY_PLANE_3_BIT_EXT = new VkImageAspectFlagBits(1024);
    public static final VkImageAspectFlagBits VK_IMAGE_ASPECT_METADATA_BIT = new VkImageAspectFlagBits(8);
    public static final VkImageAspectFlagBits VK_IMAGE_ASPECT_NONE = new VkImageAspectFlagBits(0);
    public static final VkImageAspectFlagBits VK_IMAGE_ASPECT_NONE_KHR = new VkImageAspectFlagBits(0);
    public static final VkImageAspectFlagBits VK_IMAGE_ASPECT_PLANE_0_BIT = new VkImageAspectFlagBits(16);
    public static final VkImageAspectFlagBits VK_IMAGE_ASPECT_PLANE_0_BIT_KHR = new VkImageAspectFlagBits(16);
    public static final VkImageAspectFlagBits VK_IMAGE_ASPECT_PLANE_1_BIT = new VkImageAspectFlagBits(32);
    public static final VkImageAspectFlagBits VK_IMAGE_ASPECT_PLANE_1_BIT_KHR = new VkImageAspectFlagBits(32);
    public static final VkImageAspectFlagBits VK_IMAGE_ASPECT_PLANE_2_BIT = new VkImageAspectFlagBits(64);
    public static final VkImageAspectFlagBits VK_IMAGE_ASPECT_PLANE_2_BIT_KHR = new VkImageAspectFlagBits(64);
    public static final VkImageAspectFlagBits VK_IMAGE_ASPECT_STENCIL_BIT = new VkImageAspectFlagBits(4);

    public static VkImageAspectFlagBits fromValue(int value) {
        return switch (value) {
            case 1 -> VK_IMAGE_ASPECT_COLOR_BIT;
            case 2 -> VK_IMAGE_ASPECT_DEPTH_BIT;
            case 2147483647 -> VK_IMAGE_ASPECT_FLAG_BITS_MAX_ENUM;
            case 128 -> VK_IMAGE_ASPECT_MEMORY_PLANE_0_BIT_EXT;
            case 256 -> VK_IMAGE_ASPECT_MEMORY_PLANE_1_BIT_EXT;
            case 512 -> VK_IMAGE_ASPECT_MEMORY_PLANE_2_BIT_EXT;
            case 1024 -> VK_IMAGE_ASPECT_MEMORY_PLANE_3_BIT_EXT;
            case 8 -> VK_IMAGE_ASPECT_METADATA_BIT;
            case 0 -> VK_IMAGE_ASPECT_NONE;
            case 16 -> VK_IMAGE_ASPECT_PLANE_0_BIT;
            case 32 -> VK_IMAGE_ASPECT_PLANE_1_BIT;
            case 64 -> VK_IMAGE_ASPECT_PLANE_2_BIT;
            case 4 -> VK_IMAGE_ASPECT_STENCIL_BIT;
            default -> new VkImageAspectFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
