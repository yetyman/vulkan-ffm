package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkCoarseSampleOrderTypeNV
 * Generated from jextract bindings
 */
public record VkCoarseSampleOrderTypeNV(int value) {

    public static final VkCoarseSampleOrderTypeNV VK_COARSE_SAMPLE_ORDER_TYPE_CUSTOM_NV = new VkCoarseSampleOrderTypeNV(1);
    public static final VkCoarseSampleOrderTypeNV VK_COARSE_SAMPLE_ORDER_TYPE_DEFAULT_NV = new VkCoarseSampleOrderTypeNV(0);
    public static final VkCoarseSampleOrderTypeNV VK_COARSE_SAMPLE_ORDER_TYPE_MAX_ENUM_NV = new VkCoarseSampleOrderTypeNV(2147483647);
    public static final VkCoarseSampleOrderTypeNV VK_COARSE_SAMPLE_ORDER_TYPE_PIXEL_MAJOR_NV = new VkCoarseSampleOrderTypeNV(2);
    public static final VkCoarseSampleOrderTypeNV VK_COARSE_SAMPLE_ORDER_TYPE_SAMPLE_MAJOR_NV = new VkCoarseSampleOrderTypeNV(3);

    public static VkCoarseSampleOrderTypeNV fromValue(int value) {
        return switch (value) {
            case 1 -> VK_COARSE_SAMPLE_ORDER_TYPE_CUSTOM_NV;
            case 0 -> VK_COARSE_SAMPLE_ORDER_TYPE_DEFAULT_NV;
            case 2147483647 -> VK_COARSE_SAMPLE_ORDER_TYPE_MAX_ENUM_NV;
            case 2 -> VK_COARSE_SAMPLE_ORDER_TYPE_PIXEL_MAJOR_NV;
            case 3 -> VK_COARSE_SAMPLE_ORDER_TYPE_SAMPLE_MAJOR_NV;
            default -> new VkCoarseSampleOrderTypeNV(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
