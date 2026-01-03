package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkPipelineCacheHeaderVersion
 * Generated from jextract bindings
 */
public record VkPipelineCacheHeaderVersion(int value) {

    public static final VkPipelineCacheHeaderVersion VK_PIPELINE_CACHE_HEADER_VERSION_DATA_GRAPH_QCOM = new VkPipelineCacheHeaderVersion(1000629000);
    public static final VkPipelineCacheHeaderVersion VK_PIPELINE_CACHE_HEADER_VERSION_MAX_ENUM = new VkPipelineCacheHeaderVersion(2147483647);
    public static final VkPipelineCacheHeaderVersion VK_PIPELINE_CACHE_HEADER_VERSION_ONE = new VkPipelineCacheHeaderVersion(1);

    public static VkPipelineCacheHeaderVersion fromValue(int value) {
        return switch (value) {
            case 1000629000 -> VK_PIPELINE_CACHE_HEADER_VERSION_DATA_GRAPH_QCOM;
            case 2147483647 -> VK_PIPELINE_CACHE_HEADER_VERSION_MAX_ENUM;
            case 1 -> VK_PIPELINE_CACHE_HEADER_VERSION_ONE;
            default -> new VkPipelineCacheHeaderVersion(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
