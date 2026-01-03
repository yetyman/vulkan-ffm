package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkPerformanceParameterTypeINTEL
 * Generated from jextract bindings
 */
public record VkPerformanceParameterTypeINTEL(int value) {

    public static final VkPerformanceParameterTypeINTEL VK_PERFORMANCE_PARAMETER_TYPE_HW_COUNTERS_SUPPORTED_INTEL = new VkPerformanceParameterTypeINTEL(0);
    public static final VkPerformanceParameterTypeINTEL VK_PERFORMANCE_PARAMETER_TYPE_MAX_ENUM_INTEL = new VkPerformanceParameterTypeINTEL(2147483647);
    public static final VkPerformanceParameterTypeINTEL VK_PERFORMANCE_PARAMETER_TYPE_STREAM_MARKER_VALID_BITS_INTEL = new VkPerformanceParameterTypeINTEL(1);

    public static VkPerformanceParameterTypeINTEL fromValue(int value) {
        return switch (value) {
            case 0 -> VK_PERFORMANCE_PARAMETER_TYPE_HW_COUNTERS_SUPPORTED_INTEL;
            case 2147483647 -> VK_PERFORMANCE_PARAMETER_TYPE_MAX_ENUM_INTEL;
            case 1 -> VK_PERFORMANCE_PARAMETER_TYPE_STREAM_MARKER_VALID_BITS_INTEL;
            default -> new VkPerformanceParameterTypeINTEL(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
