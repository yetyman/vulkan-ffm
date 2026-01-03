package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkIndirectCommandsLayoutUsageFlagBitsEXT
 * Generated from jextract bindings
 */
public record VkIndirectCommandsLayoutUsageFlagBitsEXT(int value) {

    public static final VkIndirectCommandsLayoutUsageFlagBitsEXT VK_INDIRECT_COMMANDS_LAYOUT_USAGE_EXPLICIT_PREPROCESS_BIT_EXT = new VkIndirectCommandsLayoutUsageFlagBitsEXT(1);
    public static final VkIndirectCommandsLayoutUsageFlagBitsEXT VK_INDIRECT_COMMANDS_LAYOUT_USAGE_FLAG_BITS_MAX_ENUM_EXT = new VkIndirectCommandsLayoutUsageFlagBitsEXT(2147483647);
    public static final VkIndirectCommandsLayoutUsageFlagBitsEXT VK_INDIRECT_COMMANDS_LAYOUT_USAGE_UNORDERED_SEQUENCES_BIT_EXT = new VkIndirectCommandsLayoutUsageFlagBitsEXT(2);

    public static VkIndirectCommandsLayoutUsageFlagBitsEXT fromValue(int value) {
        return switch (value) {
            case 1 -> VK_INDIRECT_COMMANDS_LAYOUT_USAGE_EXPLICIT_PREPROCESS_BIT_EXT;
            case 2147483647 -> VK_INDIRECT_COMMANDS_LAYOUT_USAGE_FLAG_BITS_MAX_ENUM_EXT;
            case 2 -> VK_INDIRECT_COMMANDS_LAYOUT_USAGE_UNORDERED_SEQUENCES_BIT_EXT;
            default -> new VkIndirectCommandsLayoutUsageFlagBitsEXT(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
