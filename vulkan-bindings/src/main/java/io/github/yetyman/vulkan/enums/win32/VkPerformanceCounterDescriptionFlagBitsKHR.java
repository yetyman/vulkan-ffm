package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkPerformanceCounterDescriptionFlagBitsKHR
 * Generated from jextract bindings
 */
public record VkPerformanceCounterDescriptionFlagBitsKHR(int value) {

    public static final VkPerformanceCounterDescriptionFlagBitsKHR VK_PERFORMANCE_COUNTER_DESCRIPTION_CONCURRENTLY_IMPACTED_BIT_KHR = new VkPerformanceCounterDescriptionFlagBitsKHR(2);
    public static final VkPerformanceCounterDescriptionFlagBitsKHR VK_PERFORMANCE_COUNTER_DESCRIPTION_CONCURRENTLY_IMPACTED_KHR = new VkPerformanceCounterDescriptionFlagBitsKHR(2);
    public static final VkPerformanceCounterDescriptionFlagBitsKHR VK_PERFORMANCE_COUNTER_DESCRIPTION_FLAG_BITS_MAX_ENUM_KHR = new VkPerformanceCounterDescriptionFlagBitsKHR(2147483647);
    public static final VkPerformanceCounterDescriptionFlagBitsKHR VK_PERFORMANCE_COUNTER_DESCRIPTION_PERFORMANCE_IMPACTING_BIT_KHR = new VkPerformanceCounterDescriptionFlagBitsKHR(1);
    public static final VkPerformanceCounterDescriptionFlagBitsKHR VK_PERFORMANCE_COUNTER_DESCRIPTION_PERFORMANCE_IMPACTING_KHR = new VkPerformanceCounterDescriptionFlagBitsKHR(1);

    public static VkPerformanceCounterDescriptionFlagBitsKHR fromValue(int value) {
        return switch (value) {
            case 2 -> VK_PERFORMANCE_COUNTER_DESCRIPTION_CONCURRENTLY_IMPACTED_KHR;
            case 2147483647 -> VK_PERFORMANCE_COUNTER_DESCRIPTION_FLAG_BITS_MAX_ENUM_KHR;
            case 1 -> VK_PERFORMANCE_COUNTER_DESCRIPTION_PERFORMANCE_IMPACTING_KHR;
            default -> new VkPerformanceCounterDescriptionFlagBitsKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
