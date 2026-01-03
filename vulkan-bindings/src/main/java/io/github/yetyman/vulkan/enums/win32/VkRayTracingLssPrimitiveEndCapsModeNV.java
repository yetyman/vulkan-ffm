package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkRayTracingLssPrimitiveEndCapsModeNV
 * Generated from jextract bindings
 */
public record VkRayTracingLssPrimitiveEndCapsModeNV(int value) {

    public static final VkRayTracingLssPrimitiveEndCapsModeNV VK_RAY_TRACING_LSS_PRIMITIVE_END_CAPS_MODE_CHAINED_NV = new VkRayTracingLssPrimitiveEndCapsModeNV(1);
    public static final VkRayTracingLssPrimitiveEndCapsModeNV VK_RAY_TRACING_LSS_PRIMITIVE_END_CAPS_MODE_MAX_ENUM_NV = new VkRayTracingLssPrimitiveEndCapsModeNV(2147483647);
    public static final VkRayTracingLssPrimitiveEndCapsModeNV VK_RAY_TRACING_LSS_PRIMITIVE_END_CAPS_MODE_NONE_NV = new VkRayTracingLssPrimitiveEndCapsModeNV(0);

    public static VkRayTracingLssPrimitiveEndCapsModeNV fromValue(int value) {
        return switch (value) {
            case 1 -> VK_RAY_TRACING_LSS_PRIMITIVE_END_CAPS_MODE_CHAINED_NV;
            case 2147483647 -> VK_RAY_TRACING_LSS_PRIMITIVE_END_CAPS_MODE_MAX_ENUM_NV;
            case 0 -> VK_RAY_TRACING_LSS_PRIMITIVE_END_CAPS_MODE_NONE_NV;
            default -> new VkRayTracingLssPrimitiveEndCapsModeNV(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
