package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkPipelineBindPoint
 * Generated from jextract bindings
 */
public record VkPipelineBindPoint(int value) {

    public static final VkPipelineBindPoint VK_PIPELINE_BIND_POINT_COMPUTE = new VkPipelineBindPoint(1);
    public static final VkPipelineBindPoint VK_PIPELINE_BIND_POINT_DATA_GRAPH_ARM = new VkPipelineBindPoint(1000507000);
    public static final VkPipelineBindPoint VK_PIPELINE_BIND_POINT_GRAPHICS = new VkPipelineBindPoint(0);
    public static final VkPipelineBindPoint VK_PIPELINE_BIND_POINT_MAX_ENUM = new VkPipelineBindPoint(2147483647);
    public static final VkPipelineBindPoint VK_PIPELINE_BIND_POINT_RAY_TRACING_KHR = new VkPipelineBindPoint(1000165000);
    public static final VkPipelineBindPoint VK_PIPELINE_BIND_POINT_RAY_TRACING_NV = VK_PIPELINE_BIND_POINT_RAY_TRACING_KHR;
    public static final VkPipelineBindPoint VK_PIPELINE_BIND_POINT_SUBPASS_SHADING_HUAWEI = new VkPipelineBindPoint(1000369003);

    public static VkPipelineBindPoint fromValue(int value) {
        return switch (value) {
            case 1 -> VK_PIPELINE_BIND_POINT_COMPUTE;
            case 1000507000 -> VK_PIPELINE_BIND_POINT_DATA_GRAPH_ARM;
            case 0 -> VK_PIPELINE_BIND_POINT_GRAPHICS;
            case 2147483647 -> VK_PIPELINE_BIND_POINT_MAX_ENUM;
            case 1000165000 -> VK_PIPELINE_BIND_POINT_RAY_TRACING_NV;
            case 1000369003 -> VK_PIPELINE_BIND_POINT_SUBPASS_SHADING_HUAWEI;
            default -> new VkPipelineBindPoint(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
