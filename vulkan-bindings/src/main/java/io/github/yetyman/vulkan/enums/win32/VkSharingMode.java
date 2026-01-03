package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkSharingMode
 * Generated from jextract bindings
 */
public record VkSharingMode(int value) {

    public static final VkSharingMode VK_SHARING_MODE_CONCURRENT = new VkSharingMode(1);
    public static final VkSharingMode VK_SHARING_MODE_EXCLUSIVE = new VkSharingMode(0);
    public static final VkSharingMode VK_SHARING_MODE_MAX_ENUM = new VkSharingMode(2147483647);

    public static VkSharingMode fromValue(int value) {
        return switch (value) {
            case 1 -> VK_SHARING_MODE_CONCURRENT;
            case 0 -> VK_SHARING_MODE_EXCLUSIVE;
            case 2147483647 -> VK_SHARING_MODE_MAX_ENUM;
            default -> new VkSharingMode(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
