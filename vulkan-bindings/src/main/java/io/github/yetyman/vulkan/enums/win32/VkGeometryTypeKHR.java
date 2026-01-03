package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkGeometryTypeKHR
 * Generated from jextract bindings
 */
public record VkGeometryTypeKHR(int value) {

    public static final VkGeometryTypeKHR VK_GEOMETRY_TYPE_AABBS_KHR = new VkGeometryTypeKHR(1);
    public static final VkGeometryTypeKHR VK_GEOMETRY_TYPE_AABBS_NV = new VkGeometryTypeKHR(1);
    public static final VkGeometryTypeKHR VK_GEOMETRY_TYPE_INSTANCES_KHR = new VkGeometryTypeKHR(2);
    public static final VkGeometryTypeKHR VK_GEOMETRY_TYPE_LINEAR_SWEPT_SPHERES_NV = new VkGeometryTypeKHR(1000429005);
    public static final VkGeometryTypeKHR VK_GEOMETRY_TYPE_MAX_ENUM_KHR = new VkGeometryTypeKHR(2147483647);
    public static final VkGeometryTypeKHR VK_GEOMETRY_TYPE_SPHERES_NV = new VkGeometryTypeKHR(1000429004);
    public static final VkGeometryTypeKHR VK_GEOMETRY_TYPE_TRIANGLES_KHR = new VkGeometryTypeKHR(0);
    public static final VkGeometryTypeKHR VK_GEOMETRY_TYPE_TRIANGLES_NV = new VkGeometryTypeKHR(0);

    public static VkGeometryTypeKHR fromValue(int value) {
        return switch (value) {
            case 1 -> VK_GEOMETRY_TYPE_AABBS_NV;
            case 2 -> VK_GEOMETRY_TYPE_INSTANCES_KHR;
            case 1000429005 -> VK_GEOMETRY_TYPE_LINEAR_SWEPT_SPHERES_NV;
            case 2147483647 -> VK_GEOMETRY_TYPE_MAX_ENUM_KHR;
            case 1000429004 -> VK_GEOMETRY_TYPE_SPHERES_NV;
            case 0 -> VK_GEOMETRY_TYPE_TRIANGLES_NV;
            default -> new VkGeometryTypeKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
