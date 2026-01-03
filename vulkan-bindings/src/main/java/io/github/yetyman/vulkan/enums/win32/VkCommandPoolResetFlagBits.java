package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkCommandPoolResetFlagBits
 * Generated from jextract bindings
 */
public record VkCommandPoolResetFlagBits(int value) {

    public static final VkCommandPoolResetFlagBits VK_COMMAND_POOL_RESET_FLAG_BITS_MAX_ENUM = new VkCommandPoolResetFlagBits(2147483647);
    public static final VkCommandPoolResetFlagBits VK_COMMAND_POOL_RESET_RELEASE_RESOURCES_BIT = new VkCommandPoolResetFlagBits(1);

    public static VkCommandPoolResetFlagBits fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_COMMAND_POOL_RESET_FLAG_BITS_MAX_ENUM;
            case 1 -> VK_COMMAND_POOL_RESET_RELEASE_RESOURCES_BIT;
            default -> new VkCommandPoolResetFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
