package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkPerformanceCounterUnitKHR
 * Generated from jextract bindings
 */
public record VkPerformanceCounterUnitKHR(int value) {

    public static final VkPerformanceCounterUnitKHR VK_PERFORMANCE_COUNTER_UNIT_AMPS_KHR = new VkPerformanceCounterUnitKHR(8);
    public static final VkPerformanceCounterUnitKHR VK_PERFORMANCE_COUNTER_UNIT_BYTES_KHR = new VkPerformanceCounterUnitKHR(3);
    public static final VkPerformanceCounterUnitKHR VK_PERFORMANCE_COUNTER_UNIT_BYTES_PER_SECOND_KHR = new VkPerformanceCounterUnitKHR(4);
    public static final VkPerformanceCounterUnitKHR VK_PERFORMANCE_COUNTER_UNIT_CYCLES_KHR = new VkPerformanceCounterUnitKHR(10);
    public static final VkPerformanceCounterUnitKHR VK_PERFORMANCE_COUNTER_UNIT_GENERIC_KHR = new VkPerformanceCounterUnitKHR(0);
    public static final VkPerformanceCounterUnitKHR VK_PERFORMANCE_COUNTER_UNIT_HERTZ_KHR = new VkPerformanceCounterUnitKHR(9);
    public static final VkPerformanceCounterUnitKHR VK_PERFORMANCE_COUNTER_UNIT_KELVIN_KHR = new VkPerformanceCounterUnitKHR(5);
    public static final VkPerformanceCounterUnitKHR VK_PERFORMANCE_COUNTER_UNIT_MAX_ENUM_KHR = new VkPerformanceCounterUnitKHR(2147483647);
    public static final VkPerformanceCounterUnitKHR VK_PERFORMANCE_COUNTER_UNIT_NANOSECONDS_KHR = new VkPerformanceCounterUnitKHR(2);
    public static final VkPerformanceCounterUnitKHR VK_PERFORMANCE_COUNTER_UNIT_PERCENTAGE_KHR = new VkPerformanceCounterUnitKHR(1);
    public static final VkPerformanceCounterUnitKHR VK_PERFORMANCE_COUNTER_UNIT_VOLTS_KHR = new VkPerformanceCounterUnitKHR(7);
    public static final VkPerformanceCounterUnitKHR VK_PERFORMANCE_COUNTER_UNIT_WATTS_KHR = new VkPerformanceCounterUnitKHR(6);

    public static VkPerformanceCounterUnitKHR fromValue(int value) {
        return switch (value) {
            case 8 -> VK_PERFORMANCE_COUNTER_UNIT_AMPS_KHR;
            case 3 -> VK_PERFORMANCE_COUNTER_UNIT_BYTES_KHR;
            case 4 -> VK_PERFORMANCE_COUNTER_UNIT_BYTES_PER_SECOND_KHR;
            case 10 -> VK_PERFORMANCE_COUNTER_UNIT_CYCLES_KHR;
            case 0 -> VK_PERFORMANCE_COUNTER_UNIT_GENERIC_KHR;
            case 9 -> VK_PERFORMANCE_COUNTER_UNIT_HERTZ_KHR;
            case 5 -> VK_PERFORMANCE_COUNTER_UNIT_KELVIN_KHR;
            case 2147483647 -> VK_PERFORMANCE_COUNTER_UNIT_MAX_ENUM_KHR;
            case 2 -> VK_PERFORMANCE_COUNTER_UNIT_NANOSECONDS_KHR;
            case 1 -> VK_PERFORMANCE_COUNTER_UNIT_PERCENTAGE_KHR;
            case 7 -> VK_PERFORMANCE_COUNTER_UNIT_VOLTS_KHR;
            case 6 -> VK_PERFORMANCE_COUNTER_UNIT_WATTS_KHR;
            default -> new VkPerformanceCounterUnitKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
