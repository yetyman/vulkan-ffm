package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkSubmitFlagBits
 * Generated from jextract bindings
 */
public record VkSubmitFlagBits(int value) {

    public static final VkSubmitFlagBits VK_SUBMIT_FLAG_BITS_MAX_ENUM = new VkSubmitFlagBits(2147483647);
    public static final VkSubmitFlagBits VK_SUBMIT_PROTECTED_BIT = new VkSubmitFlagBits(1);
    public static final VkSubmitFlagBits VK_SUBMIT_PROTECTED_BIT_KHR = VK_SUBMIT_PROTECTED_BIT;

    public static VkSubmitFlagBits fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_SUBMIT_FLAG_BITS_MAX_ENUM;
            case 1 -> VK_SUBMIT_PROTECTED_BIT;
            default -> new VkSubmitFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
