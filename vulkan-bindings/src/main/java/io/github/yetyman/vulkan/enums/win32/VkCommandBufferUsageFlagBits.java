package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkCommandBufferUsageFlagBits
 * Generated from jextract bindings
 */
public record VkCommandBufferUsageFlagBits(int value) {

    public static final VkCommandBufferUsageFlagBits VK_COMMAND_BUFFER_USAGE_FLAG_BITS_MAX_ENUM = new VkCommandBufferUsageFlagBits(2147483647);
    public static final VkCommandBufferUsageFlagBits VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT = new VkCommandBufferUsageFlagBits(1);
    public static final VkCommandBufferUsageFlagBits VK_COMMAND_BUFFER_USAGE_RENDER_PASS_CONTINUE_BIT = new VkCommandBufferUsageFlagBits(2);
    public static final VkCommandBufferUsageFlagBits VK_COMMAND_BUFFER_USAGE_SIMULTANEOUS_USE_BIT = new VkCommandBufferUsageFlagBits(4);

    public static VkCommandBufferUsageFlagBits fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_COMMAND_BUFFER_USAGE_FLAG_BITS_MAX_ENUM;
            case 1 -> VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
            case 2 -> VK_COMMAND_BUFFER_USAGE_RENDER_PASS_CONTINUE_BIT;
            case 4 -> VK_COMMAND_BUFFER_USAGE_SIMULTANEOUS_USE_BIT;
            default -> new VkCommandBufferUsageFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
