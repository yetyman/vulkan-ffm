package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkRayTracingInvocationReorderModeEXT
 * Generated from jextract bindings
 */
public record VkRayTracingInvocationReorderModeEXT(int value) {

    public static final VkRayTracingInvocationReorderModeEXT VK_RAY_TRACING_INVOCATION_REORDER_MODE_MAX_ENUM_EXT = new VkRayTracingInvocationReorderModeEXT(2147483647);
    public static final VkRayTracingInvocationReorderModeEXT VK_RAY_TRACING_INVOCATION_REORDER_MODE_NONE_EXT = new VkRayTracingInvocationReorderModeEXT(0);
    public static final VkRayTracingInvocationReorderModeEXT VK_RAY_TRACING_INVOCATION_REORDER_MODE_NONE_NV = VK_RAY_TRACING_INVOCATION_REORDER_MODE_NONE_EXT;
    public static final VkRayTracingInvocationReorderModeEXT VK_RAY_TRACING_INVOCATION_REORDER_MODE_REORDER_EXT = new VkRayTracingInvocationReorderModeEXT(1);
    public static final VkRayTracingInvocationReorderModeEXT VK_RAY_TRACING_INVOCATION_REORDER_MODE_REORDER_NV = VK_RAY_TRACING_INVOCATION_REORDER_MODE_REORDER_EXT;

    public static VkRayTracingInvocationReorderModeEXT fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_RAY_TRACING_INVOCATION_REORDER_MODE_MAX_ENUM_EXT;
            case 0 -> VK_RAY_TRACING_INVOCATION_REORDER_MODE_NONE_NV;
            case 1 -> VK_RAY_TRACING_INVOCATION_REORDER_MODE_REORDER_NV;
            default -> new VkRayTracingInvocationReorderModeEXT(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
