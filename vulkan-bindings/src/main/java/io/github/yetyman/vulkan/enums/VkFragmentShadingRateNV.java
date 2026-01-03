package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkFragmentShadingRateNV
 * Generated from jextract bindings
 */
public record VkFragmentShadingRateNV(int value) {

    public static final VkFragmentShadingRateNV VK_FRAGMENT_SHADING_RATE_16_INVOCATIONS_PER_PIXEL_NV = new VkFragmentShadingRateNV(14);
    public static final VkFragmentShadingRateNV VK_FRAGMENT_SHADING_RATE_1_INVOCATION_PER_1X2_PIXELS_NV = new VkFragmentShadingRateNV(1);
    public static final VkFragmentShadingRateNV VK_FRAGMENT_SHADING_RATE_1_INVOCATION_PER_2X1_PIXELS_NV = new VkFragmentShadingRateNV(4);
    public static final VkFragmentShadingRateNV VK_FRAGMENT_SHADING_RATE_1_INVOCATION_PER_2X2_PIXELS_NV = new VkFragmentShadingRateNV(5);
    public static final VkFragmentShadingRateNV VK_FRAGMENT_SHADING_RATE_1_INVOCATION_PER_2X4_PIXELS_NV = new VkFragmentShadingRateNV(6);
    public static final VkFragmentShadingRateNV VK_FRAGMENT_SHADING_RATE_1_INVOCATION_PER_4X2_PIXELS_NV = new VkFragmentShadingRateNV(9);
    public static final VkFragmentShadingRateNV VK_FRAGMENT_SHADING_RATE_1_INVOCATION_PER_4X4_PIXELS_NV = new VkFragmentShadingRateNV(10);
    public static final VkFragmentShadingRateNV VK_FRAGMENT_SHADING_RATE_1_INVOCATION_PER_PIXEL_NV = new VkFragmentShadingRateNV(0);
    public static final VkFragmentShadingRateNV VK_FRAGMENT_SHADING_RATE_2_INVOCATIONS_PER_PIXEL_NV = new VkFragmentShadingRateNV(11);
    public static final VkFragmentShadingRateNV VK_FRAGMENT_SHADING_RATE_4_INVOCATIONS_PER_PIXEL_NV = new VkFragmentShadingRateNV(12);
    public static final VkFragmentShadingRateNV VK_FRAGMENT_SHADING_RATE_8_INVOCATIONS_PER_PIXEL_NV = new VkFragmentShadingRateNV(13);
    public static final VkFragmentShadingRateNV VK_FRAGMENT_SHADING_RATE_MAX_ENUM_NV = new VkFragmentShadingRateNV(2147483647);
    public static final VkFragmentShadingRateNV VK_FRAGMENT_SHADING_RATE_NO_INVOCATIONS_NV = new VkFragmentShadingRateNV(15);

    public static VkFragmentShadingRateNV fromValue(int value) {
        return switch (value) {
            case 14 -> VK_FRAGMENT_SHADING_RATE_16_INVOCATIONS_PER_PIXEL_NV;
            case 1 -> VK_FRAGMENT_SHADING_RATE_1_INVOCATION_PER_1X2_PIXELS_NV;
            case 4 -> VK_FRAGMENT_SHADING_RATE_1_INVOCATION_PER_2X1_PIXELS_NV;
            case 5 -> VK_FRAGMENT_SHADING_RATE_1_INVOCATION_PER_2X2_PIXELS_NV;
            case 6 -> VK_FRAGMENT_SHADING_RATE_1_INVOCATION_PER_2X4_PIXELS_NV;
            case 9 -> VK_FRAGMENT_SHADING_RATE_1_INVOCATION_PER_4X2_PIXELS_NV;
            case 10 -> VK_FRAGMENT_SHADING_RATE_1_INVOCATION_PER_4X4_PIXELS_NV;
            case 0 -> VK_FRAGMENT_SHADING_RATE_1_INVOCATION_PER_PIXEL_NV;
            case 11 -> VK_FRAGMENT_SHADING_RATE_2_INVOCATIONS_PER_PIXEL_NV;
            case 12 -> VK_FRAGMENT_SHADING_RATE_4_INVOCATIONS_PER_PIXEL_NV;
            case 13 -> VK_FRAGMENT_SHADING_RATE_8_INVOCATIONS_PER_PIXEL_NV;
            case 2147483647 -> VK_FRAGMENT_SHADING_RATE_MAX_ENUM_NV;
            case 15 -> VK_FRAGMENT_SHADING_RATE_NO_INVOCATIONS_NV;
            default -> new VkFragmentShadingRateNV(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
