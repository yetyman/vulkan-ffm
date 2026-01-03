package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkDataGraphPipelineSessionBindPointTypeARM
 * Generated from jextract bindings
 */
public record VkDataGraphPipelineSessionBindPointTypeARM(int value) {

    public static final VkDataGraphPipelineSessionBindPointTypeARM VK_DATA_GRAPH_PIPELINE_SESSION_BIND_POINT_TYPE_MAX_ENUM_ARM = new VkDataGraphPipelineSessionBindPointTypeARM(2147483647);
    public static final VkDataGraphPipelineSessionBindPointTypeARM VK_DATA_GRAPH_PIPELINE_SESSION_BIND_POINT_TYPE_MEMORY_ARM = new VkDataGraphPipelineSessionBindPointTypeARM(0);

    public static VkDataGraphPipelineSessionBindPointTypeARM fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_DATA_GRAPH_PIPELINE_SESSION_BIND_POINT_TYPE_MAX_ENUM_ARM;
            case 0 -> VK_DATA_GRAPH_PIPELINE_SESSION_BIND_POINT_TYPE_MEMORY_ARM;
            default -> new VkDataGraphPipelineSessionBindPointTypeARM(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
