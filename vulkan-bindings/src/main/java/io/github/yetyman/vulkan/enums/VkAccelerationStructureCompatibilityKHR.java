package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkAccelerationStructureCompatibilityKHR
 * Generated from jextract bindings
 */
public record VkAccelerationStructureCompatibilityKHR(int value) {

    public static final VkAccelerationStructureCompatibilityKHR VK_ACCELERATION_STRUCTURE_COMPATIBILITY_COMPATIBLE_KHR = new VkAccelerationStructureCompatibilityKHR(0);
    public static final VkAccelerationStructureCompatibilityKHR VK_ACCELERATION_STRUCTURE_COMPATIBILITY_INCOMPATIBLE_KHR = new VkAccelerationStructureCompatibilityKHR(1);
    public static final VkAccelerationStructureCompatibilityKHR VK_ACCELERATION_STRUCTURE_COMPATIBILITY_MAX_ENUM_KHR = new VkAccelerationStructureCompatibilityKHR(2147483647);

    public static VkAccelerationStructureCompatibilityKHR fromValue(int value) {
        return switch (value) {
            case 0 -> VK_ACCELERATION_STRUCTURE_COMPATIBILITY_COMPATIBLE_KHR;
            case 1 -> VK_ACCELERATION_STRUCTURE_COMPATIBILITY_INCOMPATIBLE_KHR;
            case 2147483647 -> VK_ACCELERATION_STRUCTURE_COMPATIBILITY_MAX_ENUM_KHR;
            default -> new VkAccelerationStructureCompatibilityKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
