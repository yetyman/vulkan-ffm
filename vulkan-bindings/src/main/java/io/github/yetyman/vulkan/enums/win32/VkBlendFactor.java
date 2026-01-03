package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkBlendFactor
 * Generated from jextract bindings
 */
public record VkBlendFactor(int value) {

    public static final VkBlendFactor VK_BLEND_FACTOR_CONSTANT_ALPHA = new VkBlendFactor(12);
    public static final VkBlendFactor VK_BLEND_FACTOR_CONSTANT_COLOR = new VkBlendFactor(10);
    public static final VkBlendFactor VK_BLEND_FACTOR_DST_ALPHA = new VkBlendFactor(8);
    public static final VkBlendFactor VK_BLEND_FACTOR_DST_COLOR = new VkBlendFactor(4);
    public static final VkBlendFactor VK_BLEND_FACTOR_MAX_ENUM = new VkBlendFactor(2147483647);
    public static final VkBlendFactor VK_BLEND_FACTOR_ONE = new VkBlendFactor(1);
    public static final VkBlendFactor VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_ALPHA = new VkBlendFactor(13);
    public static final VkBlendFactor VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_COLOR = new VkBlendFactor(11);
    public static final VkBlendFactor VK_BLEND_FACTOR_ONE_MINUS_DST_ALPHA = new VkBlendFactor(9);
    public static final VkBlendFactor VK_BLEND_FACTOR_ONE_MINUS_DST_COLOR = new VkBlendFactor(5);
    public static final VkBlendFactor VK_BLEND_FACTOR_ONE_MINUS_SRC1_ALPHA = new VkBlendFactor(18);
    public static final VkBlendFactor VK_BLEND_FACTOR_ONE_MINUS_SRC1_COLOR = new VkBlendFactor(16);
    public static final VkBlendFactor VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA = new VkBlendFactor(7);
    public static final VkBlendFactor VK_BLEND_FACTOR_ONE_MINUS_SRC_COLOR = new VkBlendFactor(3);
    public static final VkBlendFactor VK_BLEND_FACTOR_SRC1_ALPHA = new VkBlendFactor(17);
    public static final VkBlendFactor VK_BLEND_FACTOR_SRC1_COLOR = new VkBlendFactor(15);
    public static final VkBlendFactor VK_BLEND_FACTOR_SRC_ALPHA = new VkBlendFactor(6);
    public static final VkBlendFactor VK_BLEND_FACTOR_SRC_ALPHA_SATURATE = new VkBlendFactor(14);
    public static final VkBlendFactor VK_BLEND_FACTOR_SRC_COLOR = new VkBlendFactor(2);
    public static final VkBlendFactor VK_BLEND_FACTOR_ZERO = new VkBlendFactor(0);

    public static VkBlendFactor fromValue(int value) {
        return switch (value) {
            case 12 -> VK_BLEND_FACTOR_CONSTANT_ALPHA;
            case 10 -> VK_BLEND_FACTOR_CONSTANT_COLOR;
            case 8 -> VK_BLEND_FACTOR_DST_ALPHA;
            case 4 -> VK_BLEND_FACTOR_DST_COLOR;
            case 2147483647 -> VK_BLEND_FACTOR_MAX_ENUM;
            case 1 -> VK_BLEND_FACTOR_ONE;
            case 13 -> VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_ALPHA;
            case 11 -> VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_COLOR;
            case 9 -> VK_BLEND_FACTOR_ONE_MINUS_DST_ALPHA;
            case 5 -> VK_BLEND_FACTOR_ONE_MINUS_DST_COLOR;
            case 18 -> VK_BLEND_FACTOR_ONE_MINUS_SRC1_ALPHA;
            case 16 -> VK_BLEND_FACTOR_ONE_MINUS_SRC1_COLOR;
            case 7 -> VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
            case 3 -> VK_BLEND_FACTOR_ONE_MINUS_SRC_COLOR;
            case 17 -> VK_BLEND_FACTOR_SRC1_ALPHA;
            case 15 -> VK_BLEND_FACTOR_SRC1_COLOR;
            case 6 -> VK_BLEND_FACTOR_SRC_ALPHA;
            case 14 -> VK_BLEND_FACTOR_SRC_ALPHA_SATURATE;
            case 2 -> VK_BLEND_FACTOR_SRC_COLOR;
            case 0 -> VK_BLEND_FACTOR_ZERO;
            default -> new VkBlendFactor(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
