package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkCommandBufferLevel
 * Generated from jextract bindings
 */
public record VkCommandBufferLevel(int value) {

    public static final VkCommandBufferLevel VK_COMMAND_BUFFER_LEVEL_MAX_ENUM = new VkCommandBufferLevel(2147483647);
    public static final VkCommandBufferLevel VK_COMMAND_BUFFER_LEVEL_PRIMARY = new VkCommandBufferLevel(0);
    public static final VkCommandBufferLevel VK_COMMAND_BUFFER_LEVEL_SECONDARY = new VkCommandBufferLevel(1);

    public static VkCommandBufferLevel fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_COMMAND_BUFFER_LEVEL_MAX_ENUM;
            case 0 -> VK_COMMAND_BUFFER_LEVEL_PRIMARY;
            case 1 -> VK_COMMAND_BUFFER_LEVEL_SECONDARY;
            default -> new VkCommandBufferLevel(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
