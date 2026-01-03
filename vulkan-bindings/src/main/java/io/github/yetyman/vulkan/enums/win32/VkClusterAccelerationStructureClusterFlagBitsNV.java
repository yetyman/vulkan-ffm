package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkClusterAccelerationStructureClusterFlagBitsNV
 * Generated from jextract bindings
 */
public record VkClusterAccelerationStructureClusterFlagBitsNV(int value) {

    public static final VkClusterAccelerationStructureClusterFlagBitsNV VK_CLUSTER_ACCELERATION_STRUCTURE_CLUSTER_ALLOW_DISABLE_OPACITY_MICROMAPS_NV = new VkClusterAccelerationStructureClusterFlagBitsNV(1);
    public static final VkClusterAccelerationStructureClusterFlagBitsNV VK_CLUSTER_ACCELERATION_STRUCTURE_CLUSTER_FLAG_BITS_MAX_ENUM_NV = new VkClusterAccelerationStructureClusterFlagBitsNV(2147483647);

    public static VkClusterAccelerationStructureClusterFlagBitsNV fromValue(int value) {
        return switch (value) {
            case 1 -> VK_CLUSTER_ACCELERATION_STRUCTURE_CLUSTER_ALLOW_DISABLE_OPACITY_MICROMAPS_NV;
            case 2147483647 -> VK_CLUSTER_ACCELERATION_STRUCTURE_CLUSTER_FLAG_BITS_MAX_ENUM_NV;
            default -> new VkClusterAccelerationStructureClusterFlagBitsNV(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
