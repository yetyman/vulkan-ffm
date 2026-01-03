package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkDisplayEventTypeEXT
 * Generated from jextract bindings
 */
public record VkDisplayEventTypeEXT(int value) {

    public static final VkDisplayEventTypeEXT VK_DISPLAY_EVENT_TYPE_FIRST_PIXEL_OUT_EXT = new VkDisplayEventTypeEXT(0);
    public static final VkDisplayEventTypeEXT VK_DISPLAY_EVENT_TYPE_MAX_ENUM_EXT = new VkDisplayEventTypeEXT(2147483647);

    public static VkDisplayEventTypeEXT fromValue(int value) {
        return switch (value) {
            case 0 -> VK_DISPLAY_EVENT_TYPE_FIRST_PIXEL_OUT_EXT;
            case 2147483647 -> VK_DISPLAY_EVENT_TYPE_MAX_ENUM_EXT;
            default -> new VkDisplayEventTypeEXT(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
