package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkFullScreenExclusiveEXT
 * Generated from jextract bindings
 */
public record VkFullScreenExclusiveEXT(int value) {

    public static final VkFullScreenExclusiveEXT VK_FULL_SCREEN_EXCLUSIVE_ALLOWED_EXT = new VkFullScreenExclusiveEXT(1);
    public static final VkFullScreenExclusiveEXT VK_FULL_SCREEN_EXCLUSIVE_APPLICATION_CONTROLLED_EXT = new VkFullScreenExclusiveEXT(3);
    public static final VkFullScreenExclusiveEXT VK_FULL_SCREEN_EXCLUSIVE_DEFAULT_EXT = new VkFullScreenExclusiveEXT(0);
    public static final VkFullScreenExclusiveEXT VK_FULL_SCREEN_EXCLUSIVE_DISALLOWED_EXT = new VkFullScreenExclusiveEXT(2);
    public static final VkFullScreenExclusiveEXT VK_FULL_SCREEN_EXCLUSIVE_MAX_ENUM_EXT = new VkFullScreenExclusiveEXT(2147483647);

    public static VkFullScreenExclusiveEXT fromValue(int value) {
        return switch (value) {
            case 1 -> VK_FULL_SCREEN_EXCLUSIVE_ALLOWED_EXT;
            case 3 -> VK_FULL_SCREEN_EXCLUSIVE_APPLICATION_CONTROLLED_EXT;
            case 0 -> VK_FULL_SCREEN_EXCLUSIVE_DEFAULT_EXT;
            case 2 -> VK_FULL_SCREEN_EXCLUSIVE_DISALLOWED_EXT;
            case 2147483647 -> VK_FULL_SCREEN_EXCLUSIVE_MAX_ENUM_EXT;
            default -> new VkFullScreenExclusiveEXT(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
