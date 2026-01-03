package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkFilter
 * Generated from jextract bindings
 */
public record VkFilter(int value) {

    public static final VkFilter VK_FILTER_CUBIC_EXT = new VkFilter(1000015000);
    public static final VkFilter VK_FILTER_CUBIC_IMG = new VkFilter(1000015000);
    public static final VkFilter VK_FILTER_LINEAR = new VkFilter(1);
    public static final VkFilter VK_FILTER_MAX_ENUM = new VkFilter(2147483647);
    public static final VkFilter VK_FILTER_NEAREST = new VkFilter(0);

    public static VkFilter fromValue(int value) {
        return switch (value) {
            case 1000015000 -> VK_FILTER_CUBIC_IMG;
            case 1 -> VK_FILTER_LINEAR;
            case 2147483647 -> VK_FILTER_MAX_ENUM;
            case 0 -> VK_FILTER_NEAREST;
            default -> new VkFilter(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
