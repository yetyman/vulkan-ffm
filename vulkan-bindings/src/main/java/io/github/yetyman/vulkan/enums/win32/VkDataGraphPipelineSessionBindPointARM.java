package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkDataGraphPipelineSessionBindPointARM
 * Generated from jextract bindings
 */
public record VkDataGraphPipelineSessionBindPointARM(int value) {

    public static final VkDataGraphPipelineSessionBindPointARM VK_DATA_GRAPH_PIPELINE_SESSION_BIND_POINT_MAX_ENUM_ARM = new VkDataGraphPipelineSessionBindPointARM(2147483647);
    public static final VkDataGraphPipelineSessionBindPointARM VK_DATA_GRAPH_PIPELINE_SESSION_BIND_POINT_TRANSIENT_ARM = new VkDataGraphPipelineSessionBindPointARM(0);

    public static VkDataGraphPipelineSessionBindPointARM fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_DATA_GRAPH_PIPELINE_SESSION_BIND_POINT_MAX_ENUM_ARM;
            case 0 -> VK_DATA_GRAPH_PIPELINE_SESSION_BIND_POINT_TRANSIENT_ARM;
            default -> new VkDataGraphPipelineSessionBindPointARM(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
