package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkPartitionedAccelerationStructureOpTypeNV
 * Generated from jextract bindings
 */
public record VkPartitionedAccelerationStructureOpTypeNV(int value) {

    public static final VkPartitionedAccelerationStructureOpTypeNV VK_PARTITIONED_ACCELERATION_STRUCTURE_OP_TYPE_MAX_ENUM_NV = new VkPartitionedAccelerationStructureOpTypeNV(2147483647);
    public static final VkPartitionedAccelerationStructureOpTypeNV VK_PARTITIONED_ACCELERATION_STRUCTURE_OP_TYPE_UPDATE_INSTANCE_NV = new VkPartitionedAccelerationStructureOpTypeNV(1);
    public static final VkPartitionedAccelerationStructureOpTypeNV VK_PARTITIONED_ACCELERATION_STRUCTURE_OP_TYPE_WRITE_INSTANCE_NV = new VkPartitionedAccelerationStructureOpTypeNV(0);
    public static final VkPartitionedAccelerationStructureOpTypeNV VK_PARTITIONED_ACCELERATION_STRUCTURE_OP_TYPE_WRITE_PARTITION_TRANSLATION_NV = new VkPartitionedAccelerationStructureOpTypeNV(2);

    public static VkPartitionedAccelerationStructureOpTypeNV fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_PARTITIONED_ACCELERATION_STRUCTURE_OP_TYPE_MAX_ENUM_NV;
            case 1 -> VK_PARTITIONED_ACCELERATION_STRUCTURE_OP_TYPE_UPDATE_INSTANCE_NV;
            case 0 -> VK_PARTITIONED_ACCELERATION_STRUCTURE_OP_TYPE_WRITE_INSTANCE_NV;
            case 2 -> VK_PARTITIONED_ACCELERATION_STRUCTURE_OP_TYPE_WRITE_PARTITION_TRANSLATION_NV;
            default -> new VkPartitionedAccelerationStructureOpTypeNV(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
