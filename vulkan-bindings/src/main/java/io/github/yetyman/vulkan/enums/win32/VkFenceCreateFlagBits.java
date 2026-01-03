package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkFenceCreateFlagBits
 * Generated from jextract bindings
 */
public record VkFenceCreateFlagBits(int value) {

    public static final VkFenceCreateFlagBits VK_FENCE_CREATE_FLAG_BITS_MAX_ENUM = new VkFenceCreateFlagBits(2147483647);
    public static final VkFenceCreateFlagBits VK_FENCE_CREATE_SIGNALED_BIT = new VkFenceCreateFlagBits(1);

    public static VkFenceCreateFlagBits fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_FENCE_CREATE_FLAG_BITS_MAX_ENUM;
            case 1 -> VK_FENCE_CREATE_SIGNALED_BIT;
            default -> new VkFenceCreateFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
