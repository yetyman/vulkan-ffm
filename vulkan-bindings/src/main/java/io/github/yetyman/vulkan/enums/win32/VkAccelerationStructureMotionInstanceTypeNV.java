package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkAccelerationStructureMotionInstanceTypeNV
 * Generated from jextract bindings
 */
public record VkAccelerationStructureMotionInstanceTypeNV(int value) {

    public static final VkAccelerationStructureMotionInstanceTypeNV VK_ACCELERATION_STRUCTURE_MOTION_INSTANCE_TYPE_MATRIX_MOTION_NV = new VkAccelerationStructureMotionInstanceTypeNV(1);
    public static final VkAccelerationStructureMotionInstanceTypeNV VK_ACCELERATION_STRUCTURE_MOTION_INSTANCE_TYPE_MAX_ENUM_NV = new VkAccelerationStructureMotionInstanceTypeNV(2147483647);
    public static final VkAccelerationStructureMotionInstanceTypeNV VK_ACCELERATION_STRUCTURE_MOTION_INSTANCE_TYPE_SRT_MOTION_NV = new VkAccelerationStructureMotionInstanceTypeNV(2);
    public static final VkAccelerationStructureMotionInstanceTypeNV VK_ACCELERATION_STRUCTURE_MOTION_INSTANCE_TYPE_STATIC_NV = new VkAccelerationStructureMotionInstanceTypeNV(0);

    public static VkAccelerationStructureMotionInstanceTypeNV fromValue(int value) {
        return switch (value) {
            case 1 -> VK_ACCELERATION_STRUCTURE_MOTION_INSTANCE_TYPE_MATRIX_MOTION_NV;
            case 2147483647 -> VK_ACCELERATION_STRUCTURE_MOTION_INSTANCE_TYPE_MAX_ENUM_NV;
            case 2 -> VK_ACCELERATION_STRUCTURE_MOTION_INSTANCE_TYPE_SRT_MOTION_NV;
            case 0 -> VK_ACCELERATION_STRUCTURE_MOTION_INSTANCE_TYPE_STATIC_NV;
            default -> new VkAccelerationStructureMotionInstanceTypeNV(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
