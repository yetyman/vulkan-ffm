package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkGeometryInstanceFlagBitsKHR
 * Generated from jextract bindings
 */
public record VkGeometryInstanceFlagBitsKHR(int value) {

    public static final VkGeometryInstanceFlagBitsKHR VK_GEOMETRY_INSTANCE_DISABLE_OPACITY_MICROMAPS_BIT_EXT = new VkGeometryInstanceFlagBitsKHR(32);
    public static final VkGeometryInstanceFlagBitsKHR VK_GEOMETRY_INSTANCE_DISABLE_OPACITY_MICROMAPS_EXT = VK_GEOMETRY_INSTANCE_DISABLE_OPACITY_MICROMAPS_BIT_EXT;
    public static final VkGeometryInstanceFlagBitsKHR VK_GEOMETRY_INSTANCE_FLAG_BITS_MAX_ENUM_KHR = new VkGeometryInstanceFlagBitsKHR(2147483647);
    public static final VkGeometryInstanceFlagBitsKHR VK_GEOMETRY_INSTANCE_FORCE_NO_OPAQUE_BIT_KHR = new VkGeometryInstanceFlagBitsKHR(8);
    public static final VkGeometryInstanceFlagBitsKHR VK_GEOMETRY_INSTANCE_FORCE_NO_OPAQUE_BIT_NV = VK_GEOMETRY_INSTANCE_FORCE_NO_OPAQUE_BIT_KHR;
    public static final VkGeometryInstanceFlagBitsKHR VK_GEOMETRY_INSTANCE_FORCE_OPACITY_MICROMAP_2_STATE_BIT_EXT = new VkGeometryInstanceFlagBitsKHR(16);
    public static final VkGeometryInstanceFlagBitsKHR VK_GEOMETRY_INSTANCE_FORCE_OPACITY_MICROMAP_2_STATE_EXT = VK_GEOMETRY_INSTANCE_FORCE_OPACITY_MICROMAP_2_STATE_BIT_EXT;
    public static final VkGeometryInstanceFlagBitsKHR VK_GEOMETRY_INSTANCE_FORCE_OPAQUE_BIT_KHR = new VkGeometryInstanceFlagBitsKHR(4);
    public static final VkGeometryInstanceFlagBitsKHR VK_GEOMETRY_INSTANCE_FORCE_OPAQUE_BIT_NV = VK_GEOMETRY_INSTANCE_FORCE_OPAQUE_BIT_KHR;
    public static final VkGeometryInstanceFlagBitsKHR VK_GEOMETRY_INSTANCE_TRIANGLE_CULL_DISABLE_BIT_NV = new VkGeometryInstanceFlagBitsKHR(1);
    public static final VkGeometryInstanceFlagBitsKHR VK_GEOMETRY_INSTANCE_TRIANGLE_FACING_CULL_DISABLE_BIT_KHR = VK_GEOMETRY_INSTANCE_TRIANGLE_CULL_DISABLE_BIT_NV;
    public static final VkGeometryInstanceFlagBitsKHR VK_GEOMETRY_INSTANCE_TRIANGLE_FLIP_FACING_BIT_KHR = new VkGeometryInstanceFlagBitsKHR(2);
    public static final VkGeometryInstanceFlagBitsKHR VK_GEOMETRY_INSTANCE_TRIANGLE_FRONT_COUNTERCLOCKWISE_BIT_KHR = VK_GEOMETRY_INSTANCE_TRIANGLE_FLIP_FACING_BIT_KHR;
    public static final VkGeometryInstanceFlagBitsKHR VK_GEOMETRY_INSTANCE_TRIANGLE_FRONT_COUNTERCLOCKWISE_BIT_NV = VK_GEOMETRY_INSTANCE_TRIANGLE_FLIP_FACING_BIT_KHR;

    public static VkGeometryInstanceFlagBitsKHR fromValue(int value) {
        return switch (value) {
            case 32 -> VK_GEOMETRY_INSTANCE_DISABLE_OPACITY_MICROMAPS_EXT;
            case 2147483647 -> VK_GEOMETRY_INSTANCE_FLAG_BITS_MAX_ENUM_KHR;
            case 8 -> VK_GEOMETRY_INSTANCE_FORCE_NO_OPAQUE_BIT_NV;
            case 16 -> VK_GEOMETRY_INSTANCE_FORCE_OPACITY_MICROMAP_2_STATE_EXT;
            case 4 -> VK_GEOMETRY_INSTANCE_FORCE_OPAQUE_BIT_NV;
            case 1 -> VK_GEOMETRY_INSTANCE_TRIANGLE_CULL_DISABLE_BIT_NV;
            case 2 -> VK_GEOMETRY_INSTANCE_TRIANGLE_FLIP_FACING_BIT_KHR;
            default -> new VkGeometryInstanceFlagBitsKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
