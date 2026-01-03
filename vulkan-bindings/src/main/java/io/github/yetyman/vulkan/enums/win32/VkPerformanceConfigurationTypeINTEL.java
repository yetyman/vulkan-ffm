package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkPerformanceConfigurationTypeINTEL
 * Generated from jextract bindings
 */
public record VkPerformanceConfigurationTypeINTEL(int value) {

    public static final VkPerformanceConfigurationTypeINTEL VK_PERFORMANCE_CONFIGURATION_TYPE_COMMAND_QUEUE_METRICS_DISCOVERY_ACTIVATED_INTEL = new VkPerformanceConfigurationTypeINTEL(0);
    public static final VkPerformanceConfigurationTypeINTEL VK_PERFORMANCE_CONFIGURATION_TYPE_MAX_ENUM_INTEL = new VkPerformanceConfigurationTypeINTEL(2147483647);

    public static VkPerformanceConfigurationTypeINTEL fromValue(int value) {
        return switch (value) {
            case 0 -> VK_PERFORMANCE_CONFIGURATION_TYPE_COMMAND_QUEUE_METRICS_DISCOVERY_ACTIVATED_INTEL;
            case 2147483647 -> VK_PERFORMANCE_CONFIGURATION_TYPE_MAX_ENUM_INTEL;
            default -> new VkPerformanceConfigurationTypeINTEL(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
