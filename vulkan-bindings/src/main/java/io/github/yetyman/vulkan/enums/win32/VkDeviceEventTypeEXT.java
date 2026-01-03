package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkDeviceEventTypeEXT
 * Generated from jextract bindings
 */
public record VkDeviceEventTypeEXT(int value) {

    public static final VkDeviceEventTypeEXT VK_DEVICE_EVENT_TYPE_DISPLAY_HOTPLUG_EXT = new VkDeviceEventTypeEXT(0);
    public static final VkDeviceEventTypeEXT VK_DEVICE_EVENT_TYPE_MAX_ENUM_EXT = new VkDeviceEventTypeEXT(2147483647);

    public static VkDeviceEventTypeEXT fromValue(int value) {
        return switch (value) {
            case 0 -> VK_DEVICE_EVENT_TYPE_DISPLAY_HOTPLUG_EXT;
            case 2147483647 -> VK_DEVICE_EVENT_TYPE_MAX_ENUM_EXT;
            default -> new VkDeviceEventTypeEXT(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
