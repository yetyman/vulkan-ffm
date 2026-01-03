package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkDiscardRectangleModeEXT
 * Generated from jextract bindings
 */
public record VkDiscardRectangleModeEXT(int value) {

    public static final VkDiscardRectangleModeEXT VK_DISCARD_RECTANGLE_MODE_EXCLUSIVE_EXT = new VkDiscardRectangleModeEXT(1);
    public static final VkDiscardRectangleModeEXT VK_DISCARD_RECTANGLE_MODE_INCLUSIVE_EXT = new VkDiscardRectangleModeEXT(0);
    public static final VkDiscardRectangleModeEXT VK_DISCARD_RECTANGLE_MODE_MAX_ENUM_EXT = new VkDiscardRectangleModeEXT(2147483647);

    public static VkDiscardRectangleModeEXT fromValue(int value) {
        return switch (value) {
            case 1 -> VK_DISCARD_RECTANGLE_MODE_EXCLUSIVE_EXT;
            case 0 -> VK_DISCARD_RECTANGLE_MODE_INCLUSIVE_EXT;
            case 2147483647 -> VK_DISCARD_RECTANGLE_MODE_MAX_ENUM_EXT;
            default -> new VkDiscardRectangleModeEXT(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
