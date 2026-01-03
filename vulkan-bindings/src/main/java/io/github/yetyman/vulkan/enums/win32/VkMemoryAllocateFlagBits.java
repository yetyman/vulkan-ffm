package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkMemoryAllocateFlagBits
 * Generated from jextract bindings
 */
public record VkMemoryAllocateFlagBits(int value) {

    public static final VkMemoryAllocateFlagBits VK_MEMORY_ALLOCATE_DEVICE_ADDRESS_BIT = new VkMemoryAllocateFlagBits(2);
    public static final VkMemoryAllocateFlagBits VK_MEMORY_ALLOCATE_DEVICE_ADDRESS_BIT_KHR = new VkMemoryAllocateFlagBits(2);
    public static final VkMemoryAllocateFlagBits VK_MEMORY_ALLOCATE_DEVICE_ADDRESS_CAPTURE_REPLAY_BIT = new VkMemoryAllocateFlagBits(4);
    public static final VkMemoryAllocateFlagBits VK_MEMORY_ALLOCATE_DEVICE_ADDRESS_CAPTURE_REPLAY_BIT_KHR = new VkMemoryAllocateFlagBits(4);
    public static final VkMemoryAllocateFlagBits VK_MEMORY_ALLOCATE_DEVICE_MASK_BIT = new VkMemoryAllocateFlagBits(1);
    public static final VkMemoryAllocateFlagBits VK_MEMORY_ALLOCATE_DEVICE_MASK_BIT_KHR = new VkMemoryAllocateFlagBits(1);
    public static final VkMemoryAllocateFlagBits VK_MEMORY_ALLOCATE_FLAG_BITS_MAX_ENUM = new VkMemoryAllocateFlagBits(2147483647);
    public static final VkMemoryAllocateFlagBits VK_MEMORY_ALLOCATE_ZERO_INITIALIZE_BIT_EXT = new VkMemoryAllocateFlagBits(8);

    public static VkMemoryAllocateFlagBits fromValue(int value) {
        return switch (value) {
            case 2 -> VK_MEMORY_ALLOCATE_DEVICE_ADDRESS_BIT;
            case 4 -> VK_MEMORY_ALLOCATE_DEVICE_ADDRESS_CAPTURE_REPLAY_BIT;
            case 1 -> VK_MEMORY_ALLOCATE_DEVICE_MASK_BIT;
            case 2147483647 -> VK_MEMORY_ALLOCATE_FLAG_BITS_MAX_ENUM;
            case 8 -> VK_MEMORY_ALLOCATE_ZERO_INITIALIZE_BIT_EXT;
            default -> new VkMemoryAllocateFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
