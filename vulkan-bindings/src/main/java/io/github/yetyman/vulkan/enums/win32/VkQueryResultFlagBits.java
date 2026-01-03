package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkQueryResultFlagBits
 * Generated from jextract bindings
 */
public record VkQueryResultFlagBits(int value) {

    public static final VkQueryResultFlagBits VK_QUERY_RESULT_64_BIT = new VkQueryResultFlagBits(1);
    public static final VkQueryResultFlagBits VK_QUERY_RESULT_FLAG_BITS_MAX_ENUM = new VkQueryResultFlagBits(2147483647);
    public static final VkQueryResultFlagBits VK_QUERY_RESULT_PARTIAL_BIT = new VkQueryResultFlagBits(8);
    public static final VkQueryResultFlagBits VK_QUERY_RESULT_WAIT_BIT = new VkQueryResultFlagBits(2);
    public static final VkQueryResultFlagBits VK_QUERY_RESULT_WITH_AVAILABILITY_BIT = new VkQueryResultFlagBits(4);
    public static final VkQueryResultFlagBits VK_QUERY_RESULT_WITH_STATUS_BIT_KHR = new VkQueryResultFlagBits(16);

    public static VkQueryResultFlagBits fromValue(int value) {
        return switch (value) {
            case 1 -> VK_QUERY_RESULT_64_BIT;
            case 2147483647 -> VK_QUERY_RESULT_FLAG_BITS_MAX_ENUM;
            case 8 -> VK_QUERY_RESULT_PARTIAL_BIT;
            case 2 -> VK_QUERY_RESULT_WAIT_BIT;
            case 4 -> VK_QUERY_RESULT_WITH_AVAILABILITY_BIT;
            case 16 -> VK_QUERY_RESULT_WITH_STATUS_BIT_KHR;
            default -> new VkQueryResultFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
