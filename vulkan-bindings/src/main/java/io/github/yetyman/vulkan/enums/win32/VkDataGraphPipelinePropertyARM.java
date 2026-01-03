package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkDataGraphPipelinePropertyARM
 * Generated from jextract bindings
 */
public record VkDataGraphPipelinePropertyARM(int value) {

    public static final VkDataGraphPipelinePropertyARM VK_DATA_GRAPH_PIPELINE_PROPERTY_CREATION_LOG_ARM = new VkDataGraphPipelinePropertyARM(0);
    public static final VkDataGraphPipelinePropertyARM VK_DATA_GRAPH_PIPELINE_PROPERTY_IDENTIFIER_ARM = new VkDataGraphPipelinePropertyARM(1);
    public static final VkDataGraphPipelinePropertyARM VK_DATA_GRAPH_PIPELINE_PROPERTY_MAX_ENUM_ARM = new VkDataGraphPipelinePropertyARM(2147483647);

    public static VkDataGraphPipelinePropertyARM fromValue(int value) {
        return switch (value) {
            case 0 -> VK_DATA_GRAPH_PIPELINE_PROPERTY_CREATION_LOG_ARM;
            case 1 -> VK_DATA_GRAPH_PIPELINE_PROPERTY_IDENTIFIER_ARM;
            case 2147483647 -> VK_DATA_GRAPH_PIPELINE_PROPERTY_MAX_ENUM_ARM;
            default -> new VkDataGraphPipelinePropertyARM(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
