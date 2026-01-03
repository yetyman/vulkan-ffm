package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkBuildMicromapModeEXT
 * Generated from jextract bindings
 */
public record VkBuildMicromapModeEXT(int value) {

    public static final VkBuildMicromapModeEXT VK_BUILD_MICROMAP_MODE_BUILD_EXT = new VkBuildMicromapModeEXT(0);
    public static final VkBuildMicromapModeEXT VK_BUILD_MICROMAP_MODE_MAX_ENUM_EXT = new VkBuildMicromapModeEXT(2147483647);

    public static VkBuildMicromapModeEXT fromValue(int value) {
        return switch (value) {
            case 0 -> VK_BUILD_MICROMAP_MODE_BUILD_EXT;
            case 2147483647 -> VK_BUILD_MICROMAP_MODE_MAX_ENUM_EXT;
            default -> new VkBuildMicromapModeEXT(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
