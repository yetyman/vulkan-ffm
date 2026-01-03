package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkImageViewCreateFlagBits
 * Generated from jextract bindings
 */
public record VkImageViewCreateFlagBits(int value) {

    public static final VkImageViewCreateFlagBits VK_IMAGE_VIEW_CREATE_DESCRIPTOR_BUFFER_CAPTURE_REPLAY_BIT_EXT = new VkImageViewCreateFlagBits(4);
    public static final VkImageViewCreateFlagBits VK_IMAGE_VIEW_CREATE_FLAG_BITS_MAX_ENUM = new VkImageViewCreateFlagBits(2147483647);
    public static final VkImageViewCreateFlagBits VK_IMAGE_VIEW_CREATE_FRAGMENT_DENSITY_MAP_DEFERRED_BIT_EXT = new VkImageViewCreateFlagBits(2);
    public static final VkImageViewCreateFlagBits VK_IMAGE_VIEW_CREATE_FRAGMENT_DENSITY_MAP_DYNAMIC_BIT_EXT = new VkImageViewCreateFlagBits(1);

    public static VkImageViewCreateFlagBits fromValue(int value) {
        return switch (value) {
            case 4 -> VK_IMAGE_VIEW_CREATE_DESCRIPTOR_BUFFER_CAPTURE_REPLAY_BIT_EXT;
            case 2147483647 -> VK_IMAGE_VIEW_CREATE_FLAG_BITS_MAX_ENUM;
            case 2 -> VK_IMAGE_VIEW_CREATE_FRAGMENT_DENSITY_MAP_DEFERRED_BIT_EXT;
            case 1 -> VK_IMAGE_VIEW_CREATE_FRAGMENT_DENSITY_MAP_DYNAMIC_BIT_EXT;
            default -> new VkImageViewCreateFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
