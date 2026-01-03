package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkAccelerationStructureCreateFlagBitsKHR
 * Generated from jextract bindings
 */
public record VkAccelerationStructureCreateFlagBitsKHR(int value) {

    public static final VkAccelerationStructureCreateFlagBitsKHR VK_ACCELERATION_STRUCTURE_CREATE_DESCRIPTOR_BUFFER_CAPTURE_REPLAY_BIT_EXT = new VkAccelerationStructureCreateFlagBitsKHR(8);
    public static final VkAccelerationStructureCreateFlagBitsKHR VK_ACCELERATION_STRUCTURE_CREATE_DEVICE_ADDRESS_CAPTURE_REPLAY_BIT_KHR = new VkAccelerationStructureCreateFlagBitsKHR(1);
    public static final VkAccelerationStructureCreateFlagBitsKHR VK_ACCELERATION_STRUCTURE_CREATE_FLAG_BITS_MAX_ENUM_KHR = new VkAccelerationStructureCreateFlagBitsKHR(2147483647);
    public static final VkAccelerationStructureCreateFlagBitsKHR VK_ACCELERATION_STRUCTURE_CREATE_MOTION_BIT_NV = new VkAccelerationStructureCreateFlagBitsKHR(4);

    public static VkAccelerationStructureCreateFlagBitsKHR fromValue(int value) {
        return switch (value) {
            case 8 -> VK_ACCELERATION_STRUCTURE_CREATE_DESCRIPTOR_BUFFER_CAPTURE_REPLAY_BIT_EXT;
            case 1 -> VK_ACCELERATION_STRUCTURE_CREATE_DEVICE_ADDRESS_CAPTURE_REPLAY_BIT_KHR;
            case 2147483647 -> VK_ACCELERATION_STRUCTURE_CREATE_FLAG_BITS_MAX_ENUM_KHR;
            case 4 -> VK_ACCELERATION_STRUCTURE_CREATE_MOTION_BIT_NV;
            default -> new VkAccelerationStructureCreateFlagBitsKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
