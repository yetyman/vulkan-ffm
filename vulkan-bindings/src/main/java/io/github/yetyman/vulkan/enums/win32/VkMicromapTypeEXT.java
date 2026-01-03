package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkMicromapTypeEXT
 * Generated from jextract bindings
 */
public record VkMicromapTypeEXT(int value) {

    public static final VkMicromapTypeEXT VK_MICROMAP_TYPE_MAX_ENUM_EXT = new VkMicromapTypeEXT(2147483647);
    public static final VkMicromapTypeEXT VK_MICROMAP_TYPE_OPACITY_MICROMAP_EXT = new VkMicromapTypeEXT(0);

    public static VkMicromapTypeEXT fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_MICROMAP_TYPE_MAX_ENUM_EXT;
            case 0 -> VK_MICROMAP_TYPE_OPACITY_MICROMAP_EXT;
            default -> new VkMicromapTypeEXT(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
