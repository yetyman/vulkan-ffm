package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkMicromapCreateFlagBitsEXT
 * Generated from jextract bindings
 */
public record VkMicromapCreateFlagBitsEXT(int value) {

    public static final VkMicromapCreateFlagBitsEXT VK_MICROMAP_CREATE_DEVICE_ADDRESS_CAPTURE_REPLAY_BIT_EXT = new VkMicromapCreateFlagBitsEXT(1);
    public static final VkMicromapCreateFlagBitsEXT VK_MICROMAP_CREATE_FLAG_BITS_MAX_ENUM_EXT = new VkMicromapCreateFlagBitsEXT(2147483647);

    public static VkMicromapCreateFlagBitsEXT fromValue(int value) {
        return switch (value) {
            case 1 -> VK_MICROMAP_CREATE_DEVICE_ADDRESS_CAPTURE_REPLAY_BIT_EXT;
            case 2147483647 -> VK_MICROMAP_CREATE_FLAG_BITS_MAX_ENUM_EXT;
            default -> new VkMicromapCreateFlagBitsEXT(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
