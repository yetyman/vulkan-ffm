package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkPipelineLayoutCreateFlagBits
 * Generated from jextract bindings
 */
public record VkPipelineLayoutCreateFlagBits(int value) {

    public static final VkPipelineLayoutCreateFlagBits VK_PIPELINE_LAYOUT_CREATE_FLAG_BITS_MAX_ENUM = new VkPipelineLayoutCreateFlagBits(2147483647);
    public static final VkPipelineLayoutCreateFlagBits VK_PIPELINE_LAYOUT_CREATE_INDEPENDENT_SETS_BIT_EXT = new VkPipelineLayoutCreateFlagBits(2);

    public static VkPipelineLayoutCreateFlagBits fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_PIPELINE_LAYOUT_CREATE_FLAG_BITS_MAX_ENUM;
            case 2 -> VK_PIPELINE_LAYOUT_CREATE_INDEPENDENT_SETS_BIT_EXT;
            default -> new VkPipelineLayoutCreateFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
