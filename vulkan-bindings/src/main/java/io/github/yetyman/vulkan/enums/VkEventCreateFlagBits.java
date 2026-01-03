package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkEventCreateFlagBits
 * Generated from jextract bindings
 */
public record VkEventCreateFlagBits(int value) {

    public static final VkEventCreateFlagBits VK_EVENT_CREATE_DEVICE_ONLY_BIT = new VkEventCreateFlagBits(1);
    public static final VkEventCreateFlagBits VK_EVENT_CREATE_DEVICE_ONLY_BIT_KHR = VK_EVENT_CREATE_DEVICE_ONLY_BIT;
    public static final VkEventCreateFlagBits VK_EVENT_CREATE_FLAG_BITS_MAX_ENUM = new VkEventCreateFlagBits(2147483647);

    public static VkEventCreateFlagBits fromValue(int value) {
        return switch (value) {
            case 1 -> VK_EVENT_CREATE_DEVICE_ONLY_BIT;
            case 2147483647 -> VK_EVENT_CREATE_FLAG_BITS_MAX_ENUM;
            default -> new VkEventCreateFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
