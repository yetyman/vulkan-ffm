package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkBuildAccelerationStructureModeKHR
 * Generated from jextract bindings
 */
public record VkBuildAccelerationStructureModeKHR(int value) {

    public static final VkBuildAccelerationStructureModeKHR VK_BUILD_ACCELERATION_STRUCTURE_MODE_BUILD_KHR = new VkBuildAccelerationStructureModeKHR(0);
    public static final VkBuildAccelerationStructureModeKHR VK_BUILD_ACCELERATION_STRUCTURE_MODE_MAX_ENUM_KHR = new VkBuildAccelerationStructureModeKHR(2147483647);
    public static final VkBuildAccelerationStructureModeKHR VK_BUILD_ACCELERATION_STRUCTURE_MODE_UPDATE_KHR = new VkBuildAccelerationStructureModeKHR(1);

    public static VkBuildAccelerationStructureModeKHR fromValue(int value) {
        return switch (value) {
            case 0 -> VK_BUILD_ACCELERATION_STRUCTURE_MODE_BUILD_KHR;
            case 2147483647 -> VK_BUILD_ACCELERATION_STRUCTURE_MODE_MAX_ENUM_KHR;
            case 1 -> VK_BUILD_ACCELERATION_STRUCTURE_MODE_UPDATE_KHR;
            default -> new VkBuildAccelerationStructureModeKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
