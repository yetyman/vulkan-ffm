package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkPartitionedAccelerationStructureInstanceFlagBitsNV
 * Generated from jextract bindings
 */
public record VkPartitionedAccelerationStructureInstanceFlagBitsNV(int value) {

    public static final VkPartitionedAccelerationStructureInstanceFlagBitsNV VK_PARTITIONED_ACCELERATION_STRUCTURE_INSTANCE_FLAG_BITS_MAX_ENUM_NV = new VkPartitionedAccelerationStructureInstanceFlagBitsNV(2147483647);
    public static final VkPartitionedAccelerationStructureInstanceFlagBitsNV VK_PARTITIONED_ACCELERATION_STRUCTURE_INSTANCE_FLAG_ENABLE_EXPLICIT_BOUNDING_BOX_NV = new VkPartitionedAccelerationStructureInstanceFlagBitsNV(16);
    public static final VkPartitionedAccelerationStructureInstanceFlagBitsNV VK_PARTITIONED_ACCELERATION_STRUCTURE_INSTANCE_FLAG_FORCE_NO_OPAQUE_BIT_NV = new VkPartitionedAccelerationStructureInstanceFlagBitsNV(8);
    public static final VkPartitionedAccelerationStructureInstanceFlagBitsNV VK_PARTITIONED_ACCELERATION_STRUCTURE_INSTANCE_FLAG_FORCE_OPAQUE_BIT_NV = new VkPartitionedAccelerationStructureInstanceFlagBitsNV(4);
    public static final VkPartitionedAccelerationStructureInstanceFlagBitsNV VK_PARTITIONED_ACCELERATION_STRUCTURE_INSTANCE_FLAG_TRIANGLE_FACING_CULL_DISABLE_BIT_NV = new VkPartitionedAccelerationStructureInstanceFlagBitsNV(1);
    public static final VkPartitionedAccelerationStructureInstanceFlagBitsNV VK_PARTITIONED_ACCELERATION_STRUCTURE_INSTANCE_FLAG_TRIANGLE_FLIP_FACING_BIT_NV = new VkPartitionedAccelerationStructureInstanceFlagBitsNV(2);

    public static VkPartitionedAccelerationStructureInstanceFlagBitsNV fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_PARTITIONED_ACCELERATION_STRUCTURE_INSTANCE_FLAG_BITS_MAX_ENUM_NV;
            case 16 -> VK_PARTITIONED_ACCELERATION_STRUCTURE_INSTANCE_FLAG_ENABLE_EXPLICIT_BOUNDING_BOX_NV;
            case 8 -> VK_PARTITIONED_ACCELERATION_STRUCTURE_INSTANCE_FLAG_FORCE_NO_OPAQUE_BIT_NV;
            case 4 -> VK_PARTITIONED_ACCELERATION_STRUCTURE_INSTANCE_FLAG_FORCE_OPAQUE_BIT_NV;
            case 1 -> VK_PARTITIONED_ACCELERATION_STRUCTURE_INSTANCE_FLAG_TRIANGLE_FACING_CULL_DISABLE_BIT_NV;
            case 2 -> VK_PARTITIONED_ACCELERATION_STRUCTURE_INSTANCE_FLAG_TRIANGLE_FLIP_FACING_BIT_NV;
            default -> new VkPartitionedAccelerationStructureInstanceFlagBitsNV(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
