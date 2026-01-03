package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkRenderPassCreateFlagBits
 * Generated from jextract bindings
 */
public record VkRenderPassCreateFlagBits(int value) {

    public static final VkRenderPassCreateFlagBits VK_RENDER_PASS_CREATE_FLAG_BITS_MAX_ENUM = new VkRenderPassCreateFlagBits(2147483647);
    public static final VkRenderPassCreateFlagBits VK_RENDER_PASS_CREATE_PER_LAYER_FRAGMENT_DENSITY_BIT_VALVE = new VkRenderPassCreateFlagBits(4);
    public static final VkRenderPassCreateFlagBits VK_RENDER_PASS_CREATE_TRANSFORM_BIT_QCOM = new VkRenderPassCreateFlagBits(2);

    public static VkRenderPassCreateFlagBits fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_RENDER_PASS_CREATE_FLAG_BITS_MAX_ENUM;
            case 4 -> VK_RENDER_PASS_CREATE_PER_LAYER_FRAGMENT_DENSITY_BIT_VALVE;
            case 2 -> VK_RENDER_PASS_CREATE_TRANSFORM_BIT_QCOM;
            default -> new VkRenderPassCreateFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
