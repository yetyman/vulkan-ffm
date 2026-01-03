package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkDepthClampModeEXT
 * Generated from jextract bindings
 */
public record VkDepthClampModeEXT(int value) {

    public static final VkDepthClampModeEXT VK_DEPTH_CLAMP_MODE_MAX_ENUM_EXT = new VkDepthClampModeEXT(2147483647);
    public static final VkDepthClampModeEXT VK_DEPTH_CLAMP_MODE_USER_DEFINED_RANGE_EXT = new VkDepthClampModeEXT(1);
    public static final VkDepthClampModeEXT VK_DEPTH_CLAMP_MODE_VIEWPORT_RANGE_EXT = new VkDepthClampModeEXT(0);

    public static VkDepthClampModeEXT fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_DEPTH_CLAMP_MODE_MAX_ENUM_EXT;
            case 1 -> VK_DEPTH_CLAMP_MODE_USER_DEFINED_RANGE_EXT;
            case 0 -> VK_DEPTH_CLAMP_MODE_VIEWPORT_RANGE_EXT;
            default -> new VkDepthClampModeEXT(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
