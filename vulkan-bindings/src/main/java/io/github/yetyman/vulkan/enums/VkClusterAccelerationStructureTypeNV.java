package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkClusterAccelerationStructureTypeNV
 * Generated from jextract bindings
 */
public record VkClusterAccelerationStructureTypeNV(int value) {

    public static final VkClusterAccelerationStructureTypeNV VK_CLUSTER_ACCELERATION_STRUCTURE_TYPE_CLUSTERS_BOTTOM_LEVEL_NV = new VkClusterAccelerationStructureTypeNV(0);
    public static final VkClusterAccelerationStructureTypeNV VK_CLUSTER_ACCELERATION_STRUCTURE_TYPE_MAX_ENUM_NV = new VkClusterAccelerationStructureTypeNV(2147483647);
    public static final VkClusterAccelerationStructureTypeNV VK_CLUSTER_ACCELERATION_STRUCTURE_TYPE_TRIANGLE_CLUSTER_NV = new VkClusterAccelerationStructureTypeNV(1);
    public static final VkClusterAccelerationStructureTypeNV VK_CLUSTER_ACCELERATION_STRUCTURE_TYPE_TRIANGLE_CLUSTER_TEMPLATE_NV = new VkClusterAccelerationStructureTypeNV(2);

    public static VkClusterAccelerationStructureTypeNV fromValue(int value) {
        return switch (value) {
            case 0 -> VK_CLUSTER_ACCELERATION_STRUCTURE_TYPE_CLUSTERS_BOTTOM_LEVEL_NV;
            case 2147483647 -> VK_CLUSTER_ACCELERATION_STRUCTURE_TYPE_MAX_ENUM_NV;
            case 1 -> VK_CLUSTER_ACCELERATION_STRUCTURE_TYPE_TRIANGLE_CLUSTER_NV;
            case 2 -> VK_CLUSTER_ACCELERATION_STRUCTURE_TYPE_TRIANGLE_CLUSTER_TEMPLATE_NV;
            default -> new VkClusterAccelerationStructureTypeNV(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
