package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkClusterAccelerationStructureIndexFormatFlagBitsNV
 * Generated from jextract bindings
 */
public record VkClusterAccelerationStructureIndexFormatFlagBitsNV(int value) {

    public static final VkClusterAccelerationStructureIndexFormatFlagBitsNV VK_CLUSTER_ACCELERATION_STRUCTURE_INDEX_FORMAT_16BIT_NV = new VkClusterAccelerationStructureIndexFormatFlagBitsNV(2);
    public static final VkClusterAccelerationStructureIndexFormatFlagBitsNV VK_CLUSTER_ACCELERATION_STRUCTURE_INDEX_FORMAT_32BIT_NV = new VkClusterAccelerationStructureIndexFormatFlagBitsNV(4);
    public static final VkClusterAccelerationStructureIndexFormatFlagBitsNV VK_CLUSTER_ACCELERATION_STRUCTURE_INDEX_FORMAT_8BIT_NV = new VkClusterAccelerationStructureIndexFormatFlagBitsNV(1);
    public static final VkClusterAccelerationStructureIndexFormatFlagBitsNV VK_CLUSTER_ACCELERATION_STRUCTURE_INDEX_FORMAT_FLAG_BITS_MAX_ENUM_NV = new VkClusterAccelerationStructureIndexFormatFlagBitsNV(2147483647);

    public static VkClusterAccelerationStructureIndexFormatFlagBitsNV fromValue(int value) {
        return switch (value) {
            case 2 -> VK_CLUSTER_ACCELERATION_STRUCTURE_INDEX_FORMAT_16BIT_NV;
            case 4 -> VK_CLUSTER_ACCELERATION_STRUCTURE_INDEX_FORMAT_32BIT_NV;
            case 1 -> VK_CLUSTER_ACCELERATION_STRUCTURE_INDEX_FORMAT_8BIT_NV;
            case 2147483647 -> VK_CLUSTER_ACCELERATION_STRUCTURE_INDEX_FORMAT_FLAG_BITS_MAX_ENUM_NV;
            default -> new VkClusterAccelerationStructureIndexFormatFlagBitsNV(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
