package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkPresentScalingFlagBitsKHR
 * Generated from jextract bindings
 */
public record VkPresentScalingFlagBitsKHR(int value) {

    public static final VkPresentScalingFlagBitsKHR VK_PRESENT_SCALING_ASPECT_RATIO_STRETCH_BIT_EXT = new VkPresentScalingFlagBitsKHR(2);
    public static final VkPresentScalingFlagBitsKHR VK_PRESENT_SCALING_ASPECT_RATIO_STRETCH_BIT_KHR = VK_PRESENT_SCALING_ASPECT_RATIO_STRETCH_BIT_EXT;
    public static final VkPresentScalingFlagBitsKHR VK_PRESENT_SCALING_FLAG_BITS_MAX_ENUM_KHR = new VkPresentScalingFlagBitsKHR(2147483647);
    public static final VkPresentScalingFlagBitsKHR VK_PRESENT_SCALING_ONE_TO_ONE_BIT_EXT = new VkPresentScalingFlagBitsKHR(1);
    public static final VkPresentScalingFlagBitsKHR VK_PRESENT_SCALING_ONE_TO_ONE_BIT_KHR = VK_PRESENT_SCALING_ONE_TO_ONE_BIT_EXT;
    public static final VkPresentScalingFlagBitsKHR VK_PRESENT_SCALING_STRETCH_BIT_EXT = new VkPresentScalingFlagBitsKHR(4);
    public static final VkPresentScalingFlagBitsKHR VK_PRESENT_SCALING_STRETCH_BIT_KHR = VK_PRESENT_SCALING_STRETCH_BIT_EXT;

    public static VkPresentScalingFlagBitsKHR fromValue(int value) {
        return switch (value) {
            case 2 -> VK_PRESENT_SCALING_ASPECT_RATIO_STRETCH_BIT_EXT;
            case 2147483647 -> VK_PRESENT_SCALING_FLAG_BITS_MAX_ENUM_KHR;
            case 1 -> VK_PRESENT_SCALING_ONE_TO_ONE_BIT_EXT;
            case 4 -> VK_PRESENT_SCALING_STRETCH_BIT_EXT;
            default -> new VkPresentScalingFlagBitsKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
