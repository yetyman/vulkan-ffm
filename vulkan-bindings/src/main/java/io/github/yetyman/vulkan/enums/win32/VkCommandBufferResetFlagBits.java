package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkCommandBufferResetFlagBits
 * Generated from jextract bindings
 */
public record VkCommandBufferResetFlagBits(int value) {

    public static final VkCommandBufferResetFlagBits VK_COMMAND_BUFFER_RESET_FLAG_BITS_MAX_ENUM = new VkCommandBufferResetFlagBits(2147483647);
    public static final VkCommandBufferResetFlagBits VK_COMMAND_BUFFER_RESET_RELEASE_RESOURCES_BIT = new VkCommandBufferResetFlagBits(1);

    public static VkCommandBufferResetFlagBits fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_COMMAND_BUFFER_RESET_FLAG_BITS_MAX_ENUM;
            case 1 -> VK_COMMAND_BUFFER_RESET_RELEASE_RESOURCES_BIT;
            default -> new VkCommandBufferResetFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
