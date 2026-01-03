package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkFragmentShadingRateTypeNV
 * Generated from jextract bindings
 */
public record VkFragmentShadingRateTypeNV(int value) {

    public static final VkFragmentShadingRateTypeNV VK_FRAGMENT_SHADING_RATE_TYPE_ENUMS_NV = new VkFragmentShadingRateTypeNV(1);
    public static final VkFragmentShadingRateTypeNV VK_FRAGMENT_SHADING_RATE_TYPE_FRAGMENT_SIZE_NV = new VkFragmentShadingRateTypeNV(0);
    public static final VkFragmentShadingRateTypeNV VK_FRAGMENT_SHADING_RATE_TYPE_MAX_ENUM_NV = new VkFragmentShadingRateTypeNV(2147483647);

    public static VkFragmentShadingRateTypeNV fromValue(int value) {
        return switch (value) {
            case 1 -> VK_FRAGMENT_SHADING_RATE_TYPE_ENUMS_NV;
            case 0 -> VK_FRAGMENT_SHADING_RATE_TYPE_FRAGMENT_SIZE_NV;
            case 2147483647 -> VK_FRAGMENT_SHADING_RATE_TYPE_MAX_ENUM_NV;
            default -> new VkFragmentShadingRateTypeNV(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
