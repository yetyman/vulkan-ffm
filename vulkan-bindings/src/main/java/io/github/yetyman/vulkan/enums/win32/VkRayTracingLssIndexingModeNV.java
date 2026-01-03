package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkRayTracingLssIndexingModeNV
 * Generated from jextract bindings
 */
public record VkRayTracingLssIndexingModeNV(int value) {

    public static final VkRayTracingLssIndexingModeNV VK_RAY_TRACING_LSS_INDEXING_MODE_LIST_NV = new VkRayTracingLssIndexingModeNV(0);
    public static final VkRayTracingLssIndexingModeNV VK_RAY_TRACING_LSS_INDEXING_MODE_MAX_ENUM_NV = new VkRayTracingLssIndexingModeNV(2147483647);
    public static final VkRayTracingLssIndexingModeNV VK_RAY_TRACING_LSS_INDEXING_MODE_SUCCESSIVE_NV = new VkRayTracingLssIndexingModeNV(1);

    public static VkRayTracingLssIndexingModeNV fromValue(int value) {
        return switch (value) {
            case 0 -> VK_RAY_TRACING_LSS_INDEXING_MODE_LIST_NV;
            case 2147483647 -> VK_RAY_TRACING_LSS_INDEXING_MODE_MAX_ENUM_NV;
            case 1 -> VK_RAY_TRACING_LSS_INDEXING_MODE_SUCCESSIVE_NV;
            default -> new VkRayTracingLssIndexingModeNV(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
