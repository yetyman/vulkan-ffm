package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkClusterAccelerationStructureOpModeNV
 * Generated from jextract bindings
 */
public record VkClusterAccelerationStructureOpModeNV(int value) {

    public static final VkClusterAccelerationStructureOpModeNV VK_CLUSTER_ACCELERATION_STRUCTURE_OP_MODE_COMPUTE_SIZES_NV = new VkClusterAccelerationStructureOpModeNV(2);
    public static final VkClusterAccelerationStructureOpModeNV VK_CLUSTER_ACCELERATION_STRUCTURE_OP_MODE_EXPLICIT_DESTINATIONS_NV = new VkClusterAccelerationStructureOpModeNV(1);
    public static final VkClusterAccelerationStructureOpModeNV VK_CLUSTER_ACCELERATION_STRUCTURE_OP_MODE_IMPLICIT_DESTINATIONS_NV = new VkClusterAccelerationStructureOpModeNV(0);
    public static final VkClusterAccelerationStructureOpModeNV VK_CLUSTER_ACCELERATION_STRUCTURE_OP_MODE_MAX_ENUM_NV = new VkClusterAccelerationStructureOpModeNV(2147483647);

    public static VkClusterAccelerationStructureOpModeNV fromValue(int value) {
        return switch (value) {
            case 2 -> VK_CLUSTER_ACCELERATION_STRUCTURE_OP_MODE_COMPUTE_SIZES_NV;
            case 1 -> VK_CLUSTER_ACCELERATION_STRUCTURE_OP_MODE_EXPLICIT_DESTINATIONS_NV;
            case 0 -> VK_CLUSTER_ACCELERATION_STRUCTURE_OP_MODE_IMPLICIT_DESTINATIONS_NV;
            case 2147483647 -> VK_CLUSTER_ACCELERATION_STRUCTURE_OP_MODE_MAX_ENUM_NV;
            default -> new VkClusterAccelerationStructureOpModeNV(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
