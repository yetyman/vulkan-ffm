package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkPerformanceValueTypeINTEL
 * Generated from jextract bindings
 */
public record VkPerformanceValueTypeINTEL(int value) {

    public static final VkPerformanceValueTypeINTEL VK_PERFORMANCE_VALUE_TYPE_BOOL_INTEL = new VkPerformanceValueTypeINTEL(3);
    public static final VkPerformanceValueTypeINTEL VK_PERFORMANCE_VALUE_TYPE_FLOAT_INTEL = new VkPerformanceValueTypeINTEL(2);
    public static final VkPerformanceValueTypeINTEL VK_PERFORMANCE_VALUE_TYPE_MAX_ENUM_INTEL = new VkPerformanceValueTypeINTEL(2147483647);
    public static final VkPerformanceValueTypeINTEL VK_PERFORMANCE_VALUE_TYPE_STRING_INTEL = new VkPerformanceValueTypeINTEL(4);
    public static final VkPerformanceValueTypeINTEL VK_PERFORMANCE_VALUE_TYPE_UINT32_INTEL = new VkPerformanceValueTypeINTEL(0);
    public static final VkPerformanceValueTypeINTEL VK_PERFORMANCE_VALUE_TYPE_UINT64_INTEL = new VkPerformanceValueTypeINTEL(1);

    public static VkPerformanceValueTypeINTEL fromValue(int value) {
        return switch (value) {
            case 3 -> VK_PERFORMANCE_VALUE_TYPE_BOOL_INTEL;
            case 2 -> VK_PERFORMANCE_VALUE_TYPE_FLOAT_INTEL;
            case 2147483647 -> VK_PERFORMANCE_VALUE_TYPE_MAX_ENUM_INTEL;
            case 4 -> VK_PERFORMANCE_VALUE_TYPE_STRING_INTEL;
            case 0 -> VK_PERFORMANCE_VALUE_TYPE_UINT32_INTEL;
            case 1 -> VK_PERFORMANCE_VALUE_TYPE_UINT64_INTEL;
            default -> new VkPerformanceValueTypeINTEL(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
