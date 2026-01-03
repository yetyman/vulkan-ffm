package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkQueryResultStatusKHR
 * Generated from jextract bindings
 */
public record VkQueryResultStatusKHR(int value) {

    public static final VkQueryResultStatusKHR VK_QUERY_RESULT_STATUS_COMPLETE_KHR = new VkQueryResultStatusKHR(1);
    public static final VkQueryResultStatusKHR VK_QUERY_RESULT_STATUS_ERROR_KHR = new VkQueryResultStatusKHR(-1);
    public static final VkQueryResultStatusKHR VK_QUERY_RESULT_STATUS_INSUFFICIENT_BITSTREAM_BUFFER_RANGE_KHR = new VkQueryResultStatusKHR(-1000299000);
    public static final VkQueryResultStatusKHR VK_QUERY_RESULT_STATUS_MAX_ENUM_KHR = new VkQueryResultStatusKHR(2147483647);
    public static final VkQueryResultStatusKHR VK_QUERY_RESULT_STATUS_NOT_READY_KHR = new VkQueryResultStatusKHR(0);

    public static VkQueryResultStatusKHR fromValue(int value) {
        return switch (value) {
            case 1 -> VK_QUERY_RESULT_STATUS_COMPLETE_KHR;
            case -1 -> VK_QUERY_RESULT_STATUS_ERROR_KHR;
            case -1000299000 -> VK_QUERY_RESULT_STATUS_INSUFFICIENT_BITSTREAM_BUFFER_RANGE_KHR;
            case 2147483647 -> VK_QUERY_RESULT_STATUS_MAX_ENUM_KHR;
            case 0 -> VK_QUERY_RESULT_STATUS_NOT_READY_KHR;
            default -> new VkQueryResultStatusKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
