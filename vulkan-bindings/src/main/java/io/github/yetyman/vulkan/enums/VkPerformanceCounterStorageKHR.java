package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkPerformanceCounterStorageKHR
 * Generated from jextract bindings
 */
public record VkPerformanceCounterStorageKHR(int value) {

    public static final VkPerformanceCounterStorageKHR VK_PERFORMANCE_COUNTER_STORAGE_FLOAT32_KHR = new VkPerformanceCounterStorageKHR(4);
    public static final VkPerformanceCounterStorageKHR VK_PERFORMANCE_COUNTER_STORAGE_FLOAT64_KHR = new VkPerformanceCounterStorageKHR(5);
    public static final VkPerformanceCounterStorageKHR VK_PERFORMANCE_COUNTER_STORAGE_INT32_KHR = new VkPerformanceCounterStorageKHR(0);
    public static final VkPerformanceCounterStorageKHR VK_PERFORMANCE_COUNTER_STORAGE_INT64_KHR = new VkPerformanceCounterStorageKHR(1);
    public static final VkPerformanceCounterStorageKHR VK_PERFORMANCE_COUNTER_STORAGE_MAX_ENUM_KHR = new VkPerformanceCounterStorageKHR(2147483647);
    public static final VkPerformanceCounterStorageKHR VK_PERFORMANCE_COUNTER_STORAGE_UINT32_KHR = new VkPerformanceCounterStorageKHR(2);
    public static final VkPerformanceCounterStorageKHR VK_PERFORMANCE_COUNTER_STORAGE_UINT64_KHR = new VkPerformanceCounterStorageKHR(3);

    public static VkPerformanceCounterStorageKHR fromValue(int value) {
        return switch (value) {
            case 4 -> VK_PERFORMANCE_COUNTER_STORAGE_FLOAT32_KHR;
            case 5 -> VK_PERFORMANCE_COUNTER_STORAGE_FLOAT64_KHR;
            case 0 -> VK_PERFORMANCE_COUNTER_STORAGE_INT32_KHR;
            case 1 -> VK_PERFORMANCE_COUNTER_STORAGE_INT64_KHR;
            case 2147483647 -> VK_PERFORMANCE_COUNTER_STORAGE_MAX_ENUM_KHR;
            case 2 -> VK_PERFORMANCE_COUNTER_STORAGE_UINT32_KHR;
            case 3 -> VK_PERFORMANCE_COUNTER_STORAGE_UINT64_KHR;
            default -> new VkPerformanceCounterStorageKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
