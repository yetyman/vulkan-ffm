package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkPerformanceCounterScopeKHR
 * Generated from jextract bindings
 */
public record VkPerformanceCounterScopeKHR(int value) {

    public static final VkPerformanceCounterScopeKHR VK_PERFORMANCE_COUNTER_SCOPE_COMMAND_BUFFER_KHR = new VkPerformanceCounterScopeKHR(0);
    public static final VkPerformanceCounterScopeKHR VK_PERFORMANCE_COUNTER_SCOPE_COMMAND_KHR = new VkPerformanceCounterScopeKHR(2);
    public static final VkPerformanceCounterScopeKHR VK_PERFORMANCE_COUNTER_SCOPE_MAX_ENUM_KHR = new VkPerformanceCounterScopeKHR(2147483647);
    public static final VkPerformanceCounterScopeKHR VK_PERFORMANCE_COUNTER_SCOPE_RENDER_PASS_KHR = new VkPerformanceCounterScopeKHR(1);
    public static final VkPerformanceCounterScopeKHR VK_QUERY_SCOPE_COMMAND_BUFFER_KHR = new VkPerformanceCounterScopeKHR(0);
    public static final VkPerformanceCounterScopeKHR VK_QUERY_SCOPE_COMMAND_KHR = new VkPerformanceCounterScopeKHR(2);
    public static final VkPerformanceCounterScopeKHR VK_QUERY_SCOPE_RENDER_PASS_KHR = new VkPerformanceCounterScopeKHR(1);

    public static VkPerformanceCounterScopeKHR fromValue(int value) {
        return switch (value) {
            case 0 -> VK_QUERY_SCOPE_COMMAND_BUFFER_KHR;
            case 2 -> VK_QUERY_SCOPE_COMMAND_KHR;
            case 2147483647 -> VK_PERFORMANCE_COUNTER_SCOPE_MAX_ENUM_KHR;
            case 1 -> VK_QUERY_SCOPE_RENDER_PASS_KHR;
            default -> new VkPerformanceCounterScopeKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
