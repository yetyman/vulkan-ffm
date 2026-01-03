package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkIndirectStateFlagBitsNV
 * Generated from jextract bindings
 */
public record VkIndirectStateFlagBitsNV(int value) {

    public static final VkIndirectStateFlagBitsNV VK_INDIRECT_STATE_FLAG_BITS_MAX_ENUM_NV = new VkIndirectStateFlagBitsNV(2147483647);
    public static final VkIndirectStateFlagBitsNV VK_INDIRECT_STATE_FLAG_FRONTFACE_BIT_NV = new VkIndirectStateFlagBitsNV(1);

    public static VkIndirectStateFlagBitsNV fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_INDIRECT_STATE_FLAG_BITS_MAX_ENUM_NV;
            case 1 -> VK_INDIRECT_STATE_FLAG_FRONTFACE_BIT_NV;
            default -> new VkIndirectStateFlagBitsNV(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
