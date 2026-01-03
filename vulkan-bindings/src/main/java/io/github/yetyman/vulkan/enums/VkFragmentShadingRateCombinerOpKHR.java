package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkFragmentShadingRateCombinerOpKHR
 * Generated from jextract bindings
 */
public record VkFragmentShadingRateCombinerOpKHR(int value) {

    public static final VkFragmentShadingRateCombinerOpKHR VK_FRAGMENT_SHADING_RATE_COMBINER_OP_KEEP_KHR = new VkFragmentShadingRateCombinerOpKHR(0);
    public static final VkFragmentShadingRateCombinerOpKHR VK_FRAGMENT_SHADING_RATE_COMBINER_OP_MAX_ENUM_KHR = new VkFragmentShadingRateCombinerOpKHR(2147483647);
    public static final VkFragmentShadingRateCombinerOpKHR VK_FRAGMENT_SHADING_RATE_COMBINER_OP_MAX_KHR = new VkFragmentShadingRateCombinerOpKHR(3);
    public static final VkFragmentShadingRateCombinerOpKHR VK_FRAGMENT_SHADING_RATE_COMBINER_OP_MIN_KHR = new VkFragmentShadingRateCombinerOpKHR(2);
    public static final VkFragmentShadingRateCombinerOpKHR VK_FRAGMENT_SHADING_RATE_COMBINER_OP_MUL_KHR = new VkFragmentShadingRateCombinerOpKHR(4);
    public static final VkFragmentShadingRateCombinerOpKHR VK_FRAGMENT_SHADING_RATE_COMBINER_OP_REPLACE_KHR = new VkFragmentShadingRateCombinerOpKHR(1);

    public static VkFragmentShadingRateCombinerOpKHR fromValue(int value) {
        return switch (value) {
            case 0 -> VK_FRAGMENT_SHADING_RATE_COMBINER_OP_KEEP_KHR;
            case 2147483647 -> VK_FRAGMENT_SHADING_RATE_COMBINER_OP_MAX_ENUM_KHR;
            case 3 -> VK_FRAGMENT_SHADING_RATE_COMBINER_OP_MAX_KHR;
            case 2 -> VK_FRAGMENT_SHADING_RATE_COMBINER_OP_MIN_KHR;
            case 4 -> VK_FRAGMENT_SHADING_RATE_COMBINER_OP_MUL_KHR;
            case 1 -> VK_FRAGMENT_SHADING_RATE_COMBINER_OP_REPLACE_KHR;
            default -> new VkFragmentShadingRateCombinerOpKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
