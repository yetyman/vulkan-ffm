package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkDisplayPowerStateEXT
 * Generated from jextract bindings
 */
public record VkDisplayPowerStateEXT(int value) {

    public static final VkDisplayPowerStateEXT VK_DISPLAY_POWER_STATE_MAX_ENUM_EXT = new VkDisplayPowerStateEXT(2147483647);
    public static final VkDisplayPowerStateEXT VK_DISPLAY_POWER_STATE_OFF_EXT = new VkDisplayPowerStateEXT(0);
    public static final VkDisplayPowerStateEXT VK_DISPLAY_POWER_STATE_ON_EXT = new VkDisplayPowerStateEXT(2);
    public static final VkDisplayPowerStateEXT VK_DISPLAY_POWER_STATE_SUSPEND_EXT = new VkDisplayPowerStateEXT(1);

    public static VkDisplayPowerStateEXT fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_DISPLAY_POWER_STATE_MAX_ENUM_EXT;
            case 0 -> VK_DISPLAY_POWER_STATE_OFF_EXT;
            case 2 -> VK_DISPLAY_POWER_STATE_ON_EXT;
            case 1 -> VK_DISPLAY_POWER_STATE_SUSPEND_EXT;
            default -> new VkDisplayPowerStateEXT(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
