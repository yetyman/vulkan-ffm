package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkPastPresentationTimingFlagBitsEXT
 * Generated from jextract bindings
 */
public record VkPastPresentationTimingFlagBitsEXT(int value) {

    public static final VkPastPresentationTimingFlagBitsEXT VK_PAST_PRESENTATION_TIMING_ALLOW_OUT_OF_ORDER_RESULTS_BIT_EXT = new VkPastPresentationTimingFlagBitsEXT(2);
    public static final VkPastPresentationTimingFlagBitsEXT VK_PAST_PRESENTATION_TIMING_ALLOW_PARTIAL_RESULTS_BIT_EXT = new VkPastPresentationTimingFlagBitsEXT(1);
    public static final VkPastPresentationTimingFlagBitsEXT VK_PAST_PRESENTATION_TIMING_FLAG_BITS_MAX_ENUM_EXT = new VkPastPresentationTimingFlagBitsEXT(2147483647);

    public static VkPastPresentationTimingFlagBitsEXT fromValue(int value) {
        return switch (value) {
            case 2 -> VK_PAST_PRESENTATION_TIMING_ALLOW_OUT_OF_ORDER_RESULTS_BIT_EXT;
            case 1 -> VK_PAST_PRESENTATION_TIMING_ALLOW_PARTIAL_RESULTS_BIT_EXT;
            case 2147483647 -> VK_PAST_PRESENTATION_TIMING_FLAG_BITS_MAX_ENUM_EXT;
            default -> new VkPastPresentationTimingFlagBitsEXT(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
