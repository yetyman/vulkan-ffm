package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkQueryPoolCreateFlagBits
 * Generated from jextract bindings
 */
public record VkQueryPoolCreateFlagBits(int value) {

    public static final VkQueryPoolCreateFlagBits VK_QUERY_POOL_CREATE_FLAG_BITS_MAX_ENUM = new VkQueryPoolCreateFlagBits(2147483647);
    public static final VkQueryPoolCreateFlagBits VK_QUERY_POOL_CREATE_RESET_BIT_KHR = new VkQueryPoolCreateFlagBits(1);

    public static VkQueryPoolCreateFlagBits fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_QUERY_POOL_CREATE_FLAG_BITS_MAX_ENUM;
            case 1 -> VK_QUERY_POOL_CREATE_RESET_BIT_KHR;
            default -> new VkQueryPoolCreateFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
