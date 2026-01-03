package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkQueryPoolSamplingModeINTEL
 * Generated from jextract bindings
 */
public record VkQueryPoolSamplingModeINTEL(int value) {

    public static final VkQueryPoolSamplingModeINTEL VK_QUERY_POOL_SAMPLING_MODE_MANUAL_INTEL = new VkQueryPoolSamplingModeINTEL(0);
    public static final VkQueryPoolSamplingModeINTEL VK_QUERY_POOL_SAMPLING_MODE_MAX_ENUM_INTEL = new VkQueryPoolSamplingModeINTEL(2147483647);

    public static VkQueryPoolSamplingModeINTEL fromValue(int value) {
        return switch (value) {
            case 0 -> VK_QUERY_POOL_SAMPLING_MODE_MANUAL_INTEL;
            case 2147483647 -> VK_QUERY_POOL_SAMPLING_MODE_MAX_ENUM_INTEL;
            default -> new VkQueryPoolSamplingModeINTEL(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
