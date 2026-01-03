package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkPipelineCacheCreateFlagBits
 * Generated from jextract bindings
 */
public record VkPipelineCacheCreateFlagBits(int value) {

    public static final VkPipelineCacheCreateFlagBits VK_PIPELINE_CACHE_CREATE_EXTERNALLY_SYNCHRONIZED_BIT = new VkPipelineCacheCreateFlagBits(1);
    public static final VkPipelineCacheCreateFlagBits VK_PIPELINE_CACHE_CREATE_EXTERNALLY_SYNCHRONIZED_BIT_EXT = VK_PIPELINE_CACHE_CREATE_EXTERNALLY_SYNCHRONIZED_BIT;
    public static final VkPipelineCacheCreateFlagBits VK_PIPELINE_CACHE_CREATE_FLAG_BITS_MAX_ENUM = new VkPipelineCacheCreateFlagBits(2147483647);
    public static final VkPipelineCacheCreateFlagBits VK_PIPELINE_CACHE_CREATE_INTERNALLY_SYNCHRONIZED_MERGE_BIT_KHR = new VkPipelineCacheCreateFlagBits(8);

    public static VkPipelineCacheCreateFlagBits fromValue(int value) {
        return switch (value) {
            case 1 -> VK_PIPELINE_CACHE_CREATE_EXTERNALLY_SYNCHRONIZED_BIT;
            case 2147483647 -> VK_PIPELINE_CACHE_CREATE_FLAG_BITS_MAX_ENUM;
            case 8 -> VK_PIPELINE_CACHE_CREATE_INTERNALLY_SYNCHRONIZED_MERGE_BIT_KHR;
            default -> new VkPipelineCacheCreateFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
