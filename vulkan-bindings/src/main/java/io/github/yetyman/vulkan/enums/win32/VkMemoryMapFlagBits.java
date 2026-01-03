package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkMemoryMapFlagBits
 * Generated from jextract bindings
 */
public record VkMemoryMapFlagBits(int value) {

    public static final VkMemoryMapFlagBits VK_MEMORY_MAP_FLAG_BITS_MAX_ENUM = new VkMemoryMapFlagBits(2147483647);
    public static final VkMemoryMapFlagBits VK_MEMORY_MAP_PLACED_BIT_EXT = new VkMemoryMapFlagBits(1);

    public static VkMemoryMapFlagBits fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_MEMORY_MAP_FLAG_BITS_MAX_ENUM;
            case 1 -> VK_MEMORY_MAP_PLACED_BIT_EXT;
            default -> new VkMemoryMapFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
