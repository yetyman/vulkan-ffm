package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkInstanceCreateFlagBits
 * Generated from jextract bindings
 */
public record VkInstanceCreateFlagBits(int value) {

    public static final VkInstanceCreateFlagBits VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR = new VkInstanceCreateFlagBits(1);
    public static final VkInstanceCreateFlagBits VK_INSTANCE_CREATE_FLAG_BITS_MAX_ENUM = new VkInstanceCreateFlagBits(2147483647);

    public static VkInstanceCreateFlagBits fromValue(int value) {
        return switch (value) {
            case 1 -> VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR;
            case 2147483647 -> VK_INSTANCE_CREATE_FLAG_BITS_MAX_ENUM;
            default -> new VkInstanceCreateFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
