package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkQueryPipelineStatisticFlagBits
 * Generated from jextract bindings
 */
public record VkQueryPipelineStatisticFlagBits(int value) {

    public static final VkQueryPipelineStatisticFlagBits VK_QUERY_PIPELINE_STATISTIC_CLIPPING_INVOCATIONS_BIT = new VkQueryPipelineStatisticFlagBits(32);
    public static final VkQueryPipelineStatisticFlagBits VK_QUERY_PIPELINE_STATISTIC_CLIPPING_PRIMITIVES_BIT = new VkQueryPipelineStatisticFlagBits(64);
    public static final VkQueryPipelineStatisticFlagBits VK_QUERY_PIPELINE_STATISTIC_CLUSTER_CULLING_SHADER_INVOCATIONS_BIT_HUAWEI = new VkQueryPipelineStatisticFlagBits(8192);
    public static final VkQueryPipelineStatisticFlagBits VK_QUERY_PIPELINE_STATISTIC_COMPUTE_SHADER_INVOCATIONS_BIT = new VkQueryPipelineStatisticFlagBits(1024);
    public static final VkQueryPipelineStatisticFlagBits VK_QUERY_PIPELINE_STATISTIC_FLAG_BITS_MAX_ENUM = new VkQueryPipelineStatisticFlagBits(2147483647);
    public static final VkQueryPipelineStatisticFlagBits VK_QUERY_PIPELINE_STATISTIC_FRAGMENT_SHADER_INVOCATIONS_BIT = new VkQueryPipelineStatisticFlagBits(128);
    public static final VkQueryPipelineStatisticFlagBits VK_QUERY_PIPELINE_STATISTIC_GEOMETRY_SHADER_INVOCATIONS_BIT = new VkQueryPipelineStatisticFlagBits(8);
    public static final VkQueryPipelineStatisticFlagBits VK_QUERY_PIPELINE_STATISTIC_GEOMETRY_SHADER_PRIMITIVES_BIT = new VkQueryPipelineStatisticFlagBits(16);
    public static final VkQueryPipelineStatisticFlagBits VK_QUERY_PIPELINE_STATISTIC_INPUT_ASSEMBLY_PRIMITIVES_BIT = new VkQueryPipelineStatisticFlagBits(2);
    public static final VkQueryPipelineStatisticFlagBits VK_QUERY_PIPELINE_STATISTIC_INPUT_ASSEMBLY_VERTICES_BIT = new VkQueryPipelineStatisticFlagBits(1);
    public static final VkQueryPipelineStatisticFlagBits VK_QUERY_PIPELINE_STATISTIC_MESH_SHADER_INVOCATIONS_BIT_EXT = new VkQueryPipelineStatisticFlagBits(4096);
    public static final VkQueryPipelineStatisticFlagBits VK_QUERY_PIPELINE_STATISTIC_TASK_SHADER_INVOCATIONS_BIT_EXT = new VkQueryPipelineStatisticFlagBits(2048);
    public static final VkQueryPipelineStatisticFlagBits VK_QUERY_PIPELINE_STATISTIC_TESSELLATION_CONTROL_SHADER_PATCHES_BIT = new VkQueryPipelineStatisticFlagBits(256);
    public static final VkQueryPipelineStatisticFlagBits VK_QUERY_PIPELINE_STATISTIC_TESSELLATION_EVALUATION_SHADER_INVOCATIONS_BIT = new VkQueryPipelineStatisticFlagBits(512);
    public static final VkQueryPipelineStatisticFlagBits VK_QUERY_PIPELINE_STATISTIC_VERTEX_SHADER_INVOCATIONS_BIT = new VkQueryPipelineStatisticFlagBits(4);

    public static VkQueryPipelineStatisticFlagBits fromValue(int value) {
        return switch (value) {
            case 32 -> VK_QUERY_PIPELINE_STATISTIC_CLIPPING_INVOCATIONS_BIT;
            case 64 -> VK_QUERY_PIPELINE_STATISTIC_CLIPPING_PRIMITIVES_BIT;
            case 8192 -> VK_QUERY_PIPELINE_STATISTIC_CLUSTER_CULLING_SHADER_INVOCATIONS_BIT_HUAWEI;
            case 1024 -> VK_QUERY_PIPELINE_STATISTIC_COMPUTE_SHADER_INVOCATIONS_BIT;
            case 2147483647 -> VK_QUERY_PIPELINE_STATISTIC_FLAG_BITS_MAX_ENUM;
            case 128 -> VK_QUERY_PIPELINE_STATISTIC_FRAGMENT_SHADER_INVOCATIONS_BIT;
            case 8 -> VK_QUERY_PIPELINE_STATISTIC_GEOMETRY_SHADER_INVOCATIONS_BIT;
            case 16 -> VK_QUERY_PIPELINE_STATISTIC_GEOMETRY_SHADER_PRIMITIVES_BIT;
            case 2 -> VK_QUERY_PIPELINE_STATISTIC_INPUT_ASSEMBLY_PRIMITIVES_BIT;
            case 1 -> VK_QUERY_PIPELINE_STATISTIC_INPUT_ASSEMBLY_VERTICES_BIT;
            case 4096 -> VK_QUERY_PIPELINE_STATISTIC_MESH_SHADER_INVOCATIONS_BIT_EXT;
            case 2048 -> VK_QUERY_PIPELINE_STATISTIC_TASK_SHADER_INVOCATIONS_BIT_EXT;
            case 256 -> VK_QUERY_PIPELINE_STATISTIC_TESSELLATION_CONTROL_SHADER_PATCHES_BIT;
            case 512 -> VK_QUERY_PIPELINE_STATISTIC_TESSELLATION_EVALUATION_SHADER_INVOCATIONS_BIT;
            case 4 -> VK_QUERY_PIPELINE_STATISTIC_VERTEX_SHADER_INVOCATIONS_BIT;
            default -> new VkQueryPipelineStatisticFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
