package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkMemoryHeapFlagBits
 * Generated from jextract bindings
 */
public record VkMemoryHeapFlagBits(int value) {

    public static final VkMemoryHeapFlagBits VK_MEMORY_HEAP_DEVICE_LOCAL_BIT = new VkMemoryHeapFlagBits(1);
    public static final VkMemoryHeapFlagBits VK_MEMORY_HEAP_FLAG_BITS_MAX_ENUM = new VkMemoryHeapFlagBits(2147483647);
    public static final VkMemoryHeapFlagBits VK_MEMORY_HEAP_MULTI_INSTANCE_BIT = new VkMemoryHeapFlagBits(2);
    public static final VkMemoryHeapFlagBits VK_MEMORY_HEAP_MULTI_INSTANCE_BIT_KHR = new VkMemoryHeapFlagBits(2);
    public static final VkMemoryHeapFlagBits VK_MEMORY_HEAP_TILE_MEMORY_BIT_QCOM = new VkMemoryHeapFlagBits(8);

    public static VkMemoryHeapFlagBits fromValue(int value) {
        return switch (value) {
            case 1 -> VK_MEMORY_HEAP_DEVICE_LOCAL_BIT;
            case 2147483647 -> VK_MEMORY_HEAP_FLAG_BITS_MAX_ENUM;
            case 2 -> VK_MEMORY_HEAP_MULTI_INSTANCE_BIT;
            case 8 -> VK_MEMORY_HEAP_TILE_MEMORY_BIT_QCOM;
            default -> new VkMemoryHeapFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
