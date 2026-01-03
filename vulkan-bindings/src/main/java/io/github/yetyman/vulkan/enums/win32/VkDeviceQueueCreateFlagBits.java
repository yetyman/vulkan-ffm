package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkDeviceQueueCreateFlagBits
 * Generated from jextract bindings
 */
public record VkDeviceQueueCreateFlagBits(int value) {

    public static final VkDeviceQueueCreateFlagBits VK_DEVICE_QUEUE_CREATE_FLAG_BITS_MAX_ENUM = new VkDeviceQueueCreateFlagBits(2147483647);
    public static final VkDeviceQueueCreateFlagBits VK_DEVICE_QUEUE_CREATE_PROTECTED_BIT = new VkDeviceQueueCreateFlagBits(1);

    public static VkDeviceQueueCreateFlagBits fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_DEVICE_QUEUE_CREATE_FLAG_BITS_MAX_ENUM;
            case 1 -> VK_DEVICE_QUEUE_CREATE_PROTECTED_BIT;
            default -> new VkDeviceQueueCreateFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
