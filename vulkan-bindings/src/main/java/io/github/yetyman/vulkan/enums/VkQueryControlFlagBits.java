package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkQueryControlFlagBits
 * Generated from jextract bindings
 */
public record VkQueryControlFlagBits(int value) {

    public static final VkQueryControlFlagBits VK_QUERY_CONTROL_FLAG_BITS_MAX_ENUM = new VkQueryControlFlagBits(2147483647);
    public static final VkQueryControlFlagBits VK_QUERY_CONTROL_PRECISE_BIT = new VkQueryControlFlagBits(1);

    public static VkQueryControlFlagBits fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_QUERY_CONTROL_FLAG_BITS_MAX_ENUM;
            case 1 -> VK_QUERY_CONTROL_PRECISE_BIT;
            default -> new VkQueryControlFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
