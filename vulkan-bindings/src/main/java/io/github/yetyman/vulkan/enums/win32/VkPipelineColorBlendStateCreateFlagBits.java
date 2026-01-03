package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkPipelineColorBlendStateCreateFlagBits
 * Generated from jextract bindings
 */
public record VkPipelineColorBlendStateCreateFlagBits(int value) {

    public static final VkPipelineColorBlendStateCreateFlagBits VK_PIPELINE_COLOR_BLEND_STATE_CREATE_FLAG_BITS_MAX_ENUM = new VkPipelineColorBlendStateCreateFlagBits(2147483647);
    public static final VkPipelineColorBlendStateCreateFlagBits VK_PIPELINE_COLOR_BLEND_STATE_CREATE_RASTERIZATION_ORDER_ATTACHMENT_ACCESS_BIT_ARM = new VkPipelineColorBlendStateCreateFlagBits(1);
    public static final VkPipelineColorBlendStateCreateFlagBits VK_PIPELINE_COLOR_BLEND_STATE_CREATE_RASTERIZATION_ORDER_ATTACHMENT_ACCESS_BIT_EXT = new VkPipelineColorBlendStateCreateFlagBits(1);

    public static VkPipelineColorBlendStateCreateFlagBits fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_PIPELINE_COLOR_BLEND_STATE_CREATE_FLAG_BITS_MAX_ENUM;
            case 1 -> VK_PIPELINE_COLOR_BLEND_STATE_CREATE_RASTERIZATION_ORDER_ATTACHMENT_ACCESS_BIT_ARM;
            default -> new VkPipelineColorBlendStateCreateFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
