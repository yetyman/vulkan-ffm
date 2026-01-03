package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkCopyAccelerationStructureModeKHR
 * Generated from jextract bindings
 */
public record VkCopyAccelerationStructureModeKHR(int value) {

    public static final VkCopyAccelerationStructureModeKHR VK_COPY_ACCELERATION_STRUCTURE_MODE_CLONE_KHR = new VkCopyAccelerationStructureModeKHR(0);
    public static final VkCopyAccelerationStructureModeKHR VK_COPY_ACCELERATION_STRUCTURE_MODE_CLONE_NV = new VkCopyAccelerationStructureModeKHR(0);
    public static final VkCopyAccelerationStructureModeKHR VK_COPY_ACCELERATION_STRUCTURE_MODE_COMPACT_KHR = new VkCopyAccelerationStructureModeKHR(1);
    public static final VkCopyAccelerationStructureModeKHR VK_COPY_ACCELERATION_STRUCTURE_MODE_COMPACT_NV = new VkCopyAccelerationStructureModeKHR(1);
    public static final VkCopyAccelerationStructureModeKHR VK_COPY_ACCELERATION_STRUCTURE_MODE_DESERIALIZE_KHR = new VkCopyAccelerationStructureModeKHR(3);
    public static final VkCopyAccelerationStructureModeKHR VK_COPY_ACCELERATION_STRUCTURE_MODE_MAX_ENUM_KHR = new VkCopyAccelerationStructureModeKHR(2147483647);
    public static final VkCopyAccelerationStructureModeKHR VK_COPY_ACCELERATION_STRUCTURE_MODE_SERIALIZE_KHR = new VkCopyAccelerationStructureModeKHR(2);

    public static VkCopyAccelerationStructureModeKHR fromValue(int value) {
        return switch (value) {
            case 0 -> VK_COPY_ACCELERATION_STRUCTURE_MODE_CLONE_NV;
            case 1 -> VK_COPY_ACCELERATION_STRUCTURE_MODE_COMPACT_NV;
            case 3 -> VK_COPY_ACCELERATION_STRUCTURE_MODE_DESERIALIZE_KHR;
            case 2147483647 -> VK_COPY_ACCELERATION_STRUCTURE_MODE_MAX_ENUM_KHR;
            case 2 -> VK_COPY_ACCELERATION_STRUCTURE_MODE_SERIALIZE_KHR;
            default -> new VkCopyAccelerationStructureModeKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
