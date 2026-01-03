package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkCommandPoolCreateFlagBits
 * Generated from jextract bindings
 */
public record VkCommandPoolCreateFlagBits(int value) {

    public static final VkCommandPoolCreateFlagBits VK_COMMAND_POOL_CREATE_FLAG_BITS_MAX_ENUM = new VkCommandPoolCreateFlagBits(2147483647);
    public static final VkCommandPoolCreateFlagBits VK_COMMAND_POOL_CREATE_PROTECTED_BIT = new VkCommandPoolCreateFlagBits(4);
    public static final VkCommandPoolCreateFlagBits VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT = new VkCommandPoolCreateFlagBits(2);
    public static final VkCommandPoolCreateFlagBits VK_COMMAND_POOL_CREATE_TRANSIENT_BIT = new VkCommandPoolCreateFlagBits(1);

    public static VkCommandPoolCreateFlagBits fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_COMMAND_POOL_CREATE_FLAG_BITS_MAX_ENUM;
            case 4 -> VK_COMMAND_POOL_CREATE_PROTECTED_BIT;
            case 2 -> VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
            case 1 -> VK_COMMAND_POOL_CREATE_TRANSIENT_BIT;
            default -> new VkCommandPoolCreateFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
