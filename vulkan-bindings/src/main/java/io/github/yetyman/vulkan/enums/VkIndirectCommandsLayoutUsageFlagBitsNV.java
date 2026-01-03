package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkIndirectCommandsLayoutUsageFlagBitsNV
 * Generated from jextract bindings
 */
public record VkIndirectCommandsLayoutUsageFlagBitsNV(int value) {

    public static final VkIndirectCommandsLayoutUsageFlagBitsNV VK_INDIRECT_COMMANDS_LAYOUT_USAGE_EXPLICIT_PREPROCESS_BIT_NV = new VkIndirectCommandsLayoutUsageFlagBitsNV(1);
    public static final VkIndirectCommandsLayoutUsageFlagBitsNV VK_INDIRECT_COMMANDS_LAYOUT_USAGE_FLAG_BITS_MAX_ENUM_NV = new VkIndirectCommandsLayoutUsageFlagBitsNV(2147483647);
    public static final VkIndirectCommandsLayoutUsageFlagBitsNV VK_INDIRECT_COMMANDS_LAYOUT_USAGE_INDEXED_SEQUENCES_BIT_NV = new VkIndirectCommandsLayoutUsageFlagBitsNV(2);
    public static final VkIndirectCommandsLayoutUsageFlagBitsNV VK_INDIRECT_COMMANDS_LAYOUT_USAGE_UNORDERED_SEQUENCES_BIT_NV = new VkIndirectCommandsLayoutUsageFlagBitsNV(4);

    public static VkIndirectCommandsLayoutUsageFlagBitsNV fromValue(int value) {
        return switch (value) {
            case 1 -> VK_INDIRECT_COMMANDS_LAYOUT_USAGE_EXPLICIT_PREPROCESS_BIT_NV;
            case 2147483647 -> VK_INDIRECT_COMMANDS_LAYOUT_USAGE_FLAG_BITS_MAX_ENUM_NV;
            case 2 -> VK_INDIRECT_COMMANDS_LAYOUT_USAGE_INDEXED_SEQUENCES_BIT_NV;
            case 4 -> VK_INDIRECT_COMMANDS_LAYOUT_USAGE_UNORDERED_SEQUENCES_BIT_NV;
            default -> new VkIndirectCommandsLayoutUsageFlagBitsNV(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
