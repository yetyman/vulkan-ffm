package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkPipelineDepthStencilStateCreateFlagBits
 * Generated from jextract bindings
 */
public record VkPipelineDepthStencilStateCreateFlagBits(int value) {

    public static final VkPipelineDepthStencilStateCreateFlagBits VK_PIPELINE_DEPTH_STENCIL_STATE_CREATE_FLAG_BITS_MAX_ENUM = new VkPipelineDepthStencilStateCreateFlagBits(2147483647);
    public static final VkPipelineDepthStencilStateCreateFlagBits VK_PIPELINE_DEPTH_STENCIL_STATE_CREATE_RASTERIZATION_ORDER_ATTACHMENT_DEPTH_ACCESS_BIT_ARM = new VkPipelineDepthStencilStateCreateFlagBits(1);
    public static final VkPipelineDepthStencilStateCreateFlagBits VK_PIPELINE_DEPTH_STENCIL_STATE_CREATE_RASTERIZATION_ORDER_ATTACHMENT_DEPTH_ACCESS_BIT_EXT = new VkPipelineDepthStencilStateCreateFlagBits(1);
    public static final VkPipelineDepthStencilStateCreateFlagBits VK_PIPELINE_DEPTH_STENCIL_STATE_CREATE_RASTERIZATION_ORDER_ATTACHMENT_STENCIL_ACCESS_BIT_ARM = new VkPipelineDepthStencilStateCreateFlagBits(2);
    public static final VkPipelineDepthStencilStateCreateFlagBits VK_PIPELINE_DEPTH_STENCIL_STATE_CREATE_RASTERIZATION_ORDER_ATTACHMENT_STENCIL_ACCESS_BIT_EXT = new VkPipelineDepthStencilStateCreateFlagBits(2);

    public static VkPipelineDepthStencilStateCreateFlagBits fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_PIPELINE_DEPTH_STENCIL_STATE_CREATE_FLAG_BITS_MAX_ENUM;
            case 1 -> VK_PIPELINE_DEPTH_STENCIL_STATE_CREATE_RASTERIZATION_ORDER_ATTACHMENT_DEPTH_ACCESS_BIT_ARM;
            case 2 -> VK_PIPELINE_DEPTH_STENCIL_STATE_CREATE_RASTERIZATION_ORDER_ATTACHMENT_STENCIL_ACCESS_BIT_ARM;
            default -> new VkPipelineDepthStencilStateCreateFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
