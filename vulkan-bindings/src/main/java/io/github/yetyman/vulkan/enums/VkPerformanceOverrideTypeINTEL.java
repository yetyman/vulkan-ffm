package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkPerformanceOverrideTypeINTEL
 * Generated from jextract bindings
 */
public record VkPerformanceOverrideTypeINTEL(int value) {

    public static final VkPerformanceOverrideTypeINTEL VK_PERFORMANCE_OVERRIDE_TYPE_FLUSH_GPU_CACHES_INTEL = new VkPerformanceOverrideTypeINTEL(1);
    public static final VkPerformanceOverrideTypeINTEL VK_PERFORMANCE_OVERRIDE_TYPE_MAX_ENUM_INTEL = new VkPerformanceOverrideTypeINTEL(2147483647);
    public static final VkPerformanceOverrideTypeINTEL VK_PERFORMANCE_OVERRIDE_TYPE_NULL_HARDWARE_INTEL = new VkPerformanceOverrideTypeINTEL(0);

    public static VkPerformanceOverrideTypeINTEL fromValue(int value) {
        return switch (value) {
            case 1 -> VK_PERFORMANCE_OVERRIDE_TYPE_FLUSH_GPU_CACHES_INTEL;
            case 2147483647 -> VK_PERFORMANCE_OVERRIDE_TYPE_MAX_ENUM_INTEL;
            case 0 -> VK_PERFORMANCE_OVERRIDE_TYPE_NULL_HARDWARE_INTEL;
            default -> new VkPerformanceOverrideTypeINTEL(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
