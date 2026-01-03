package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkAccelerationStructureTypeKHR
 * Generated from jextract bindings
 */
public record VkAccelerationStructureTypeKHR(int value) {

    public static final VkAccelerationStructureTypeKHR VK_ACCELERATION_STRUCTURE_TYPE_BOTTOM_LEVEL_KHR = new VkAccelerationStructureTypeKHR(1);
    public static final VkAccelerationStructureTypeKHR VK_ACCELERATION_STRUCTURE_TYPE_BOTTOM_LEVEL_NV = VK_ACCELERATION_STRUCTURE_TYPE_BOTTOM_LEVEL_KHR;
    public static final VkAccelerationStructureTypeKHR VK_ACCELERATION_STRUCTURE_TYPE_GENERIC_KHR = new VkAccelerationStructureTypeKHR(2);
    public static final VkAccelerationStructureTypeKHR VK_ACCELERATION_STRUCTURE_TYPE_MAX_ENUM_KHR = new VkAccelerationStructureTypeKHR(2147483647);
    public static final VkAccelerationStructureTypeKHR VK_ACCELERATION_STRUCTURE_TYPE_TOP_LEVEL_KHR = new VkAccelerationStructureTypeKHR(0);
    public static final VkAccelerationStructureTypeKHR VK_ACCELERATION_STRUCTURE_TYPE_TOP_LEVEL_NV = VK_ACCELERATION_STRUCTURE_TYPE_TOP_LEVEL_KHR;

    public static VkAccelerationStructureTypeKHR fromValue(int value) {
        return switch (value) {
            case 1 -> VK_ACCELERATION_STRUCTURE_TYPE_BOTTOM_LEVEL_NV;
            case 2 -> VK_ACCELERATION_STRUCTURE_TYPE_GENERIC_KHR;
            case 2147483647 -> VK_ACCELERATION_STRUCTURE_TYPE_MAX_ENUM_KHR;
            case 0 -> VK_ACCELERATION_STRUCTURE_TYPE_TOP_LEVEL_NV;
            default -> new VkAccelerationStructureTypeKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
