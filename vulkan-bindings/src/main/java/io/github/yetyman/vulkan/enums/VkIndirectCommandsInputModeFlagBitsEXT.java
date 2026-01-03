package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkIndirectCommandsInputModeFlagBitsEXT
 * Generated from jextract bindings
 */
public record VkIndirectCommandsInputModeFlagBitsEXT(int value) {

    public static final VkIndirectCommandsInputModeFlagBitsEXT VK_INDIRECT_COMMANDS_INPUT_MODE_DXGI_INDEX_BUFFER_EXT = new VkIndirectCommandsInputModeFlagBitsEXT(2);
    public static final VkIndirectCommandsInputModeFlagBitsEXT VK_INDIRECT_COMMANDS_INPUT_MODE_FLAG_BITS_MAX_ENUM_EXT = new VkIndirectCommandsInputModeFlagBitsEXT(2147483647);
    public static final VkIndirectCommandsInputModeFlagBitsEXT VK_INDIRECT_COMMANDS_INPUT_MODE_VULKAN_INDEX_BUFFER_EXT = new VkIndirectCommandsInputModeFlagBitsEXT(1);

    public static VkIndirectCommandsInputModeFlagBitsEXT fromValue(int value) {
        return switch (value) {
            case 2 -> VK_INDIRECT_COMMANDS_INPUT_MODE_DXGI_INDEX_BUFFER_EXT;
            case 2147483647 -> VK_INDIRECT_COMMANDS_INPUT_MODE_FLAG_BITS_MAX_ENUM_EXT;
            case 1 -> VK_INDIRECT_COMMANDS_INPUT_MODE_VULKAN_INDEX_BUFFER_EXT;
            default -> new VkIndirectCommandsInputModeFlagBitsEXT(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
