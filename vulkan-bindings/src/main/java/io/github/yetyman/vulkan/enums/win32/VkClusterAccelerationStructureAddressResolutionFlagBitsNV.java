package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkClusterAccelerationStructureAddressResolutionFlagBitsNV
 * Generated from jextract bindings
 */
public record VkClusterAccelerationStructureAddressResolutionFlagBitsNV(int value) {

    public static final VkClusterAccelerationStructureAddressResolutionFlagBitsNV VK_CLUSTER_ACCELERATION_STRUCTURE_ADDRESS_RESOLUTION_FLAG_BITS_MAX_ENUM_NV = new VkClusterAccelerationStructureAddressResolutionFlagBitsNV(2147483647);
    public static final VkClusterAccelerationStructureAddressResolutionFlagBitsNV VK_CLUSTER_ACCELERATION_STRUCTURE_ADDRESS_RESOLUTION_INDIRECTED_DST_ADDRESS_ARRAY_BIT_NV = new VkClusterAccelerationStructureAddressResolutionFlagBitsNV(4);
    public static final VkClusterAccelerationStructureAddressResolutionFlagBitsNV VK_CLUSTER_ACCELERATION_STRUCTURE_ADDRESS_RESOLUTION_INDIRECTED_DST_IMPLICIT_DATA_BIT_NV = new VkClusterAccelerationStructureAddressResolutionFlagBitsNV(1);
    public static final VkClusterAccelerationStructureAddressResolutionFlagBitsNV VK_CLUSTER_ACCELERATION_STRUCTURE_ADDRESS_RESOLUTION_INDIRECTED_DST_SIZES_ARRAY_BIT_NV = new VkClusterAccelerationStructureAddressResolutionFlagBitsNV(8);
    public static final VkClusterAccelerationStructureAddressResolutionFlagBitsNV VK_CLUSTER_ACCELERATION_STRUCTURE_ADDRESS_RESOLUTION_INDIRECTED_SCRATCH_DATA_BIT_NV = new VkClusterAccelerationStructureAddressResolutionFlagBitsNV(2);
    public static final VkClusterAccelerationStructureAddressResolutionFlagBitsNV VK_CLUSTER_ACCELERATION_STRUCTURE_ADDRESS_RESOLUTION_INDIRECTED_SRC_INFOS_ARRAY_BIT_NV = new VkClusterAccelerationStructureAddressResolutionFlagBitsNV(16);
    public static final VkClusterAccelerationStructureAddressResolutionFlagBitsNV VK_CLUSTER_ACCELERATION_STRUCTURE_ADDRESS_RESOLUTION_INDIRECTED_SRC_INFOS_COUNT_BIT_NV = new VkClusterAccelerationStructureAddressResolutionFlagBitsNV(32);
    public static final VkClusterAccelerationStructureAddressResolutionFlagBitsNV VK_CLUSTER_ACCELERATION_STRUCTURE_ADDRESS_RESOLUTION_NONE_NV = new VkClusterAccelerationStructureAddressResolutionFlagBitsNV(0);

    public static VkClusterAccelerationStructureAddressResolutionFlagBitsNV fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_CLUSTER_ACCELERATION_STRUCTURE_ADDRESS_RESOLUTION_FLAG_BITS_MAX_ENUM_NV;
            case 4 -> VK_CLUSTER_ACCELERATION_STRUCTURE_ADDRESS_RESOLUTION_INDIRECTED_DST_ADDRESS_ARRAY_BIT_NV;
            case 1 -> VK_CLUSTER_ACCELERATION_STRUCTURE_ADDRESS_RESOLUTION_INDIRECTED_DST_IMPLICIT_DATA_BIT_NV;
            case 8 -> VK_CLUSTER_ACCELERATION_STRUCTURE_ADDRESS_RESOLUTION_INDIRECTED_DST_SIZES_ARRAY_BIT_NV;
            case 2 -> VK_CLUSTER_ACCELERATION_STRUCTURE_ADDRESS_RESOLUTION_INDIRECTED_SCRATCH_DATA_BIT_NV;
            case 16 -> VK_CLUSTER_ACCELERATION_STRUCTURE_ADDRESS_RESOLUTION_INDIRECTED_SRC_INFOS_ARRAY_BIT_NV;
            case 32 -> VK_CLUSTER_ACCELERATION_STRUCTURE_ADDRESS_RESOLUTION_INDIRECTED_SRC_INFOS_COUNT_BIT_NV;
            case 0 -> VK_CLUSTER_ACCELERATION_STRUCTURE_ADDRESS_RESOLUTION_NONE_NV;
            default -> new VkClusterAccelerationStructureAddressResolutionFlagBitsNV(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
