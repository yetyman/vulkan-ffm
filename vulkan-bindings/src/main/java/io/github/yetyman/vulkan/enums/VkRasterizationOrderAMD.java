package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkRasterizationOrderAMD
 * Generated from jextract bindings
 */
public record VkRasterizationOrderAMD(int value) {

    public static final VkRasterizationOrderAMD VK_RASTERIZATION_ORDER_MAX_ENUM_AMD = new VkRasterizationOrderAMD(2147483647);
    public static final VkRasterizationOrderAMD VK_RASTERIZATION_ORDER_RELAXED_AMD = new VkRasterizationOrderAMD(1);
    public static final VkRasterizationOrderAMD VK_RASTERIZATION_ORDER_STRICT_AMD = new VkRasterizationOrderAMD(0);

    public static VkRasterizationOrderAMD fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_RASTERIZATION_ORDER_MAX_ENUM_AMD;
            case 1 -> VK_RASTERIZATION_ORDER_RELAXED_AMD;
            case 0 -> VK_RASTERIZATION_ORDER_STRICT_AMD;
            default -> new VkRasterizationOrderAMD(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
