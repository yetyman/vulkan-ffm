package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkAccelerationStructureBuildTypeKHR
 * Generated from jextract bindings
 */
public record VkAccelerationStructureBuildTypeKHR(int value) {

    public static final VkAccelerationStructureBuildTypeKHR VK_ACCELERATION_STRUCTURE_BUILD_TYPE_DEVICE_KHR = new VkAccelerationStructureBuildTypeKHR(1);
    public static final VkAccelerationStructureBuildTypeKHR VK_ACCELERATION_STRUCTURE_BUILD_TYPE_HOST_KHR = new VkAccelerationStructureBuildTypeKHR(0);
    public static final VkAccelerationStructureBuildTypeKHR VK_ACCELERATION_STRUCTURE_BUILD_TYPE_HOST_OR_DEVICE_KHR = new VkAccelerationStructureBuildTypeKHR(2);
    public static final VkAccelerationStructureBuildTypeKHR VK_ACCELERATION_STRUCTURE_BUILD_TYPE_MAX_ENUM_KHR = new VkAccelerationStructureBuildTypeKHR(2147483647);

    public static VkAccelerationStructureBuildTypeKHR fromValue(int value) {
        return switch (value) {
            case 1 -> VK_ACCELERATION_STRUCTURE_BUILD_TYPE_DEVICE_KHR;
            case 0 -> VK_ACCELERATION_STRUCTURE_BUILD_TYPE_HOST_KHR;
            case 2 -> VK_ACCELERATION_STRUCTURE_BUILD_TYPE_HOST_OR_DEVICE_KHR;
            case 2147483647 -> VK_ACCELERATION_STRUCTURE_BUILD_TYPE_MAX_ENUM_KHR;
            default -> new VkAccelerationStructureBuildTypeKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
