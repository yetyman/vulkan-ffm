package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkPresentTimingInfoFlagBitsEXT
 * Generated from jextract bindings
 */
public record VkPresentTimingInfoFlagBitsEXT(int value) {

    public static final VkPresentTimingInfoFlagBitsEXT VK_PRESENT_TIMING_INFO_FLAG_BITS_MAX_ENUM_EXT = new VkPresentTimingInfoFlagBitsEXT(2147483647);
    public static final VkPresentTimingInfoFlagBitsEXT VK_PRESENT_TIMING_INFO_PRESENT_AT_NEAREST_REFRESH_CYCLE_BIT_EXT = new VkPresentTimingInfoFlagBitsEXT(2);
    public static final VkPresentTimingInfoFlagBitsEXT VK_PRESENT_TIMING_INFO_PRESENT_AT_RELATIVE_TIME_BIT_EXT = new VkPresentTimingInfoFlagBitsEXT(1);

    public static VkPresentTimingInfoFlagBitsEXT fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_PRESENT_TIMING_INFO_FLAG_BITS_MAX_ENUM_EXT;
            case 2 -> VK_PRESENT_TIMING_INFO_PRESENT_AT_NEAREST_REFRESH_CYCLE_BIT_EXT;
            case 1 -> VK_PRESENT_TIMING_INFO_PRESENT_AT_RELATIVE_TIME_BIT_EXT;
            default -> new VkPresentTimingInfoFlagBitsEXT(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
