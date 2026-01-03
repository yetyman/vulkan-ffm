package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkRayTracingShaderGroupTypeKHR
 * Generated from jextract bindings
 */
public record VkRayTracingShaderGroupTypeKHR(int value) {

    public static final VkRayTracingShaderGroupTypeKHR VK_RAY_TRACING_SHADER_GROUP_TYPE_GENERAL_KHR = new VkRayTracingShaderGroupTypeKHR(0);
    public static final VkRayTracingShaderGroupTypeKHR VK_RAY_TRACING_SHADER_GROUP_TYPE_GENERAL_NV = VK_RAY_TRACING_SHADER_GROUP_TYPE_GENERAL_KHR;
    public static final VkRayTracingShaderGroupTypeKHR VK_RAY_TRACING_SHADER_GROUP_TYPE_MAX_ENUM_KHR = new VkRayTracingShaderGroupTypeKHR(2147483647);
    public static final VkRayTracingShaderGroupTypeKHR VK_RAY_TRACING_SHADER_GROUP_TYPE_PROCEDURAL_HIT_GROUP_KHR = new VkRayTracingShaderGroupTypeKHR(2);
    public static final VkRayTracingShaderGroupTypeKHR VK_RAY_TRACING_SHADER_GROUP_TYPE_PROCEDURAL_HIT_GROUP_NV = VK_RAY_TRACING_SHADER_GROUP_TYPE_PROCEDURAL_HIT_GROUP_KHR;
    public static final VkRayTracingShaderGroupTypeKHR VK_RAY_TRACING_SHADER_GROUP_TYPE_TRIANGLES_HIT_GROUP_KHR = new VkRayTracingShaderGroupTypeKHR(1);
    public static final VkRayTracingShaderGroupTypeKHR VK_RAY_TRACING_SHADER_GROUP_TYPE_TRIANGLES_HIT_GROUP_NV = VK_RAY_TRACING_SHADER_GROUP_TYPE_TRIANGLES_HIT_GROUP_KHR;

    public static VkRayTracingShaderGroupTypeKHR fromValue(int value) {
        return switch (value) {
            case 0 -> VK_RAY_TRACING_SHADER_GROUP_TYPE_GENERAL_NV;
            case 2147483647 -> VK_RAY_TRACING_SHADER_GROUP_TYPE_MAX_ENUM_KHR;
            case 2 -> VK_RAY_TRACING_SHADER_GROUP_TYPE_PROCEDURAL_HIT_GROUP_NV;
            case 1 -> VK_RAY_TRACING_SHADER_GROUP_TYPE_TRIANGLES_HIT_GROUP_NV;
            default -> new VkRayTracingShaderGroupTypeKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
