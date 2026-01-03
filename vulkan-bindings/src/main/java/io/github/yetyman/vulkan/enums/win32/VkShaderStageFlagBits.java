package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkShaderStageFlagBits
 * Generated from jextract bindings
 */
public record VkShaderStageFlagBits(int value) {

    public static final VkShaderStageFlagBits VK_SHADER_STAGE_ALL = new VkShaderStageFlagBits(2147483647);
    public static final VkShaderStageFlagBits VK_SHADER_STAGE_ALL_GRAPHICS = new VkShaderStageFlagBits(31);
    public static final VkShaderStageFlagBits VK_SHADER_STAGE_ANY_HIT_BIT_KHR = new VkShaderStageFlagBits(512);
    public static final VkShaderStageFlagBits VK_SHADER_STAGE_ANY_HIT_BIT_NV = new VkShaderStageFlagBits(512);
    public static final VkShaderStageFlagBits VK_SHADER_STAGE_CALLABLE_BIT_KHR = new VkShaderStageFlagBits(8192);
    public static final VkShaderStageFlagBits VK_SHADER_STAGE_CALLABLE_BIT_NV = new VkShaderStageFlagBits(8192);
    public static final VkShaderStageFlagBits VK_SHADER_STAGE_CLOSEST_HIT_BIT_KHR = new VkShaderStageFlagBits(1024);
    public static final VkShaderStageFlagBits VK_SHADER_STAGE_CLOSEST_HIT_BIT_NV = new VkShaderStageFlagBits(1024);
    public static final VkShaderStageFlagBits VK_SHADER_STAGE_CLUSTER_CULLING_BIT_HUAWEI = new VkShaderStageFlagBits(524288);
    public static final VkShaderStageFlagBits VK_SHADER_STAGE_COMPUTE_BIT = new VkShaderStageFlagBits(32);
    public static final VkShaderStageFlagBits VK_SHADER_STAGE_FLAG_BITS_MAX_ENUM = new VkShaderStageFlagBits(2147483647);
    public static final VkShaderStageFlagBits VK_SHADER_STAGE_FRAGMENT_BIT = new VkShaderStageFlagBits(16);
    public static final VkShaderStageFlagBits VK_SHADER_STAGE_GEOMETRY_BIT = new VkShaderStageFlagBits(8);
    public static final VkShaderStageFlagBits VK_SHADER_STAGE_INTERSECTION_BIT_KHR = new VkShaderStageFlagBits(4096);
    public static final VkShaderStageFlagBits VK_SHADER_STAGE_INTERSECTION_BIT_NV = new VkShaderStageFlagBits(4096);
    public static final VkShaderStageFlagBits VK_SHADER_STAGE_MESH_BIT_EXT = new VkShaderStageFlagBits(128);
    public static final VkShaderStageFlagBits VK_SHADER_STAGE_MESH_BIT_NV = new VkShaderStageFlagBits(128);
    public static final VkShaderStageFlagBits VK_SHADER_STAGE_MISS_BIT_KHR = new VkShaderStageFlagBits(2048);
    public static final VkShaderStageFlagBits VK_SHADER_STAGE_MISS_BIT_NV = new VkShaderStageFlagBits(2048);
    public static final VkShaderStageFlagBits VK_SHADER_STAGE_RAYGEN_BIT_KHR = new VkShaderStageFlagBits(256);
    public static final VkShaderStageFlagBits VK_SHADER_STAGE_RAYGEN_BIT_NV = new VkShaderStageFlagBits(256);
    public static final VkShaderStageFlagBits VK_SHADER_STAGE_SUBPASS_SHADING_BIT_HUAWEI = new VkShaderStageFlagBits(16384);
    public static final VkShaderStageFlagBits VK_SHADER_STAGE_TASK_BIT_EXT = new VkShaderStageFlagBits(64);
    public static final VkShaderStageFlagBits VK_SHADER_STAGE_TASK_BIT_NV = new VkShaderStageFlagBits(64);
    public static final VkShaderStageFlagBits VK_SHADER_STAGE_TESSELLATION_CONTROL_BIT = new VkShaderStageFlagBits(2);
    public static final VkShaderStageFlagBits VK_SHADER_STAGE_TESSELLATION_EVALUATION_BIT = new VkShaderStageFlagBits(4);
    public static final VkShaderStageFlagBits VK_SHADER_STAGE_VERTEX_BIT = new VkShaderStageFlagBits(1);

    public static VkShaderStageFlagBits fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_SHADER_STAGE_ALL;
            case 31 -> VK_SHADER_STAGE_ALL_GRAPHICS;
            case 512 -> VK_SHADER_STAGE_ANY_HIT_BIT_NV;
            case 8192 -> VK_SHADER_STAGE_CALLABLE_BIT_NV;
            case 1024 -> VK_SHADER_STAGE_CLOSEST_HIT_BIT_NV;
            case 524288 -> VK_SHADER_STAGE_CLUSTER_CULLING_BIT_HUAWEI;
            case 32 -> VK_SHADER_STAGE_COMPUTE_BIT;
            case 16 -> VK_SHADER_STAGE_FRAGMENT_BIT;
            case 8 -> VK_SHADER_STAGE_GEOMETRY_BIT;
            case 4096 -> VK_SHADER_STAGE_INTERSECTION_BIT_NV;
            case 128 -> VK_SHADER_STAGE_MESH_BIT_NV;
            case 2048 -> VK_SHADER_STAGE_MISS_BIT_NV;
            case 256 -> VK_SHADER_STAGE_RAYGEN_BIT_NV;
            case 16384 -> VK_SHADER_STAGE_SUBPASS_SHADING_BIT_HUAWEI;
            case 64 -> VK_SHADER_STAGE_TASK_BIT_NV;
            case 2 -> VK_SHADER_STAGE_TESSELLATION_CONTROL_BIT;
            case 4 -> VK_SHADER_STAGE_TESSELLATION_EVALUATION_BIT;
            case 1 -> VK_SHADER_STAGE_VERTEX_BIT;
            default -> new VkShaderStageFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
