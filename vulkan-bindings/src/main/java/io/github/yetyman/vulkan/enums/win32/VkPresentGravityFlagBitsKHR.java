package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkPresentGravityFlagBitsKHR
 * Generated from jextract bindings
 */
public record VkPresentGravityFlagBitsKHR(int value) {

    public static final VkPresentGravityFlagBitsKHR VK_PRESENT_GRAVITY_CENTERED_BIT_EXT = new VkPresentGravityFlagBitsKHR(4);
    public static final VkPresentGravityFlagBitsKHR VK_PRESENT_GRAVITY_CENTERED_BIT_KHR = new VkPresentGravityFlagBitsKHR(4);
    public static final VkPresentGravityFlagBitsKHR VK_PRESENT_GRAVITY_FLAG_BITS_MAX_ENUM_KHR = new VkPresentGravityFlagBitsKHR(2147483647);
    public static final VkPresentGravityFlagBitsKHR VK_PRESENT_GRAVITY_MAX_BIT_EXT = new VkPresentGravityFlagBitsKHR(2);
    public static final VkPresentGravityFlagBitsKHR VK_PRESENT_GRAVITY_MAX_BIT_KHR = new VkPresentGravityFlagBitsKHR(2);
    public static final VkPresentGravityFlagBitsKHR VK_PRESENT_GRAVITY_MIN_BIT_EXT = new VkPresentGravityFlagBitsKHR(1);
    public static final VkPresentGravityFlagBitsKHR VK_PRESENT_GRAVITY_MIN_BIT_KHR = new VkPresentGravityFlagBitsKHR(1);

    public static VkPresentGravityFlagBitsKHR fromValue(int value) {
        return switch (value) {
            case 4 -> VK_PRESENT_GRAVITY_CENTERED_BIT_EXT;
            case 2147483647 -> VK_PRESENT_GRAVITY_FLAG_BITS_MAX_ENUM_KHR;
            case 2 -> VK_PRESENT_GRAVITY_MAX_BIT_EXT;
            case 1 -> VK_PRESENT_GRAVITY_MIN_BIT_EXT;
            default -> new VkPresentGravityFlagBitsKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
