package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkGeometryFlagBitsKHR
 * Generated from jextract bindings
 */
public record VkGeometryFlagBitsKHR(int value) {

    public static final VkGeometryFlagBitsKHR VK_GEOMETRY_FLAG_BITS_MAX_ENUM_KHR = new VkGeometryFlagBitsKHR(2147483647);
    public static final VkGeometryFlagBitsKHR VK_GEOMETRY_NO_DUPLICATE_ANY_HIT_INVOCATION_BIT_KHR = new VkGeometryFlagBitsKHR(2);
    public static final VkGeometryFlagBitsKHR VK_GEOMETRY_NO_DUPLICATE_ANY_HIT_INVOCATION_BIT_NV = new VkGeometryFlagBitsKHR(2);
    public static final VkGeometryFlagBitsKHR VK_GEOMETRY_OPAQUE_BIT_KHR = new VkGeometryFlagBitsKHR(1);
    public static final VkGeometryFlagBitsKHR VK_GEOMETRY_OPAQUE_BIT_NV = new VkGeometryFlagBitsKHR(1);

    public static VkGeometryFlagBitsKHR fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_GEOMETRY_FLAG_BITS_MAX_ENUM_KHR;
            case 2 -> VK_GEOMETRY_NO_DUPLICATE_ANY_HIT_INVOCATION_BIT_NV;
            case 1 -> VK_GEOMETRY_OPAQUE_BIT_NV;
            default -> new VkGeometryFlagBitsKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
