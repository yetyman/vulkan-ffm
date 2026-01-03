package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkMemoryUnmapFlagBits
 * Generated from jextract bindings
 */
public record VkMemoryUnmapFlagBits(int value) {

    public static final VkMemoryUnmapFlagBits VK_MEMORY_UNMAP_FLAG_BITS_MAX_ENUM = new VkMemoryUnmapFlagBits(2147483647);
    public static final VkMemoryUnmapFlagBits VK_MEMORY_UNMAP_RESERVE_BIT_EXT = new VkMemoryUnmapFlagBits(1);

    public static VkMemoryUnmapFlagBits fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_MEMORY_UNMAP_FLAG_BITS_MAX_ENUM;
            case 1 -> VK_MEMORY_UNMAP_RESERVE_BIT_EXT;
            default -> new VkMemoryUnmapFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
