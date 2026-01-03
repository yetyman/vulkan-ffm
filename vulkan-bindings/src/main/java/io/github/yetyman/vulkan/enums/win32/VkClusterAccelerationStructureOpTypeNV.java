package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkClusterAccelerationStructureOpTypeNV
 * Generated from jextract bindings
 */
public record VkClusterAccelerationStructureOpTypeNV(int value) {

    public static final VkClusterAccelerationStructureOpTypeNV VK_CLUSTER_ACCELERATION_STRUCTURE_OP_TYPE_BUILD_CLUSTERS_BOTTOM_LEVEL_NV = new VkClusterAccelerationStructureOpTypeNV(1);
    public static final VkClusterAccelerationStructureOpTypeNV VK_CLUSTER_ACCELERATION_STRUCTURE_OP_TYPE_BUILD_TRIANGLE_CLUSTER_NV = new VkClusterAccelerationStructureOpTypeNV(2);
    public static final VkClusterAccelerationStructureOpTypeNV VK_CLUSTER_ACCELERATION_STRUCTURE_OP_TYPE_BUILD_TRIANGLE_CLUSTER_TEMPLATE_NV = new VkClusterAccelerationStructureOpTypeNV(3);
    public static final VkClusterAccelerationStructureOpTypeNV VK_CLUSTER_ACCELERATION_STRUCTURE_OP_TYPE_GET_CLUSTER_TEMPLATE_INDICES_NV = new VkClusterAccelerationStructureOpTypeNV(5);
    public static final VkClusterAccelerationStructureOpTypeNV VK_CLUSTER_ACCELERATION_STRUCTURE_OP_TYPE_INSTANTIATE_TRIANGLE_CLUSTER_NV = new VkClusterAccelerationStructureOpTypeNV(4);
    public static final VkClusterAccelerationStructureOpTypeNV VK_CLUSTER_ACCELERATION_STRUCTURE_OP_TYPE_MAX_ENUM_NV = new VkClusterAccelerationStructureOpTypeNV(2147483647);
    public static final VkClusterAccelerationStructureOpTypeNV VK_CLUSTER_ACCELERATION_STRUCTURE_OP_TYPE_MOVE_OBJECTS_NV = new VkClusterAccelerationStructureOpTypeNV(0);

    public static VkClusterAccelerationStructureOpTypeNV fromValue(int value) {
        return switch (value) {
            case 1 -> VK_CLUSTER_ACCELERATION_STRUCTURE_OP_TYPE_BUILD_CLUSTERS_BOTTOM_LEVEL_NV;
            case 2 -> VK_CLUSTER_ACCELERATION_STRUCTURE_OP_TYPE_BUILD_TRIANGLE_CLUSTER_NV;
            case 3 -> VK_CLUSTER_ACCELERATION_STRUCTURE_OP_TYPE_BUILD_TRIANGLE_CLUSTER_TEMPLATE_NV;
            case 5 -> VK_CLUSTER_ACCELERATION_STRUCTURE_OP_TYPE_GET_CLUSTER_TEMPLATE_INDICES_NV;
            case 4 -> VK_CLUSTER_ACCELERATION_STRUCTURE_OP_TYPE_INSTANTIATE_TRIANGLE_CLUSTER_NV;
            case 2147483647 -> VK_CLUSTER_ACCELERATION_STRUCTURE_OP_TYPE_MAX_ENUM_NV;
            case 0 -> VK_CLUSTER_ACCELERATION_STRUCTURE_OP_TYPE_MOVE_OBJECTS_NV;
            default -> new VkClusterAccelerationStructureOpTypeNV(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
