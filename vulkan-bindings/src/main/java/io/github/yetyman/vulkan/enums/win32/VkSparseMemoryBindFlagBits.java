package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkSparseMemoryBindFlagBits
 * Generated from jextract bindings
 */
public record VkSparseMemoryBindFlagBits(int value) {

    public static final VkSparseMemoryBindFlagBits VK_SPARSE_MEMORY_BIND_FLAG_BITS_MAX_ENUM = new VkSparseMemoryBindFlagBits(2147483647);
    public static final VkSparseMemoryBindFlagBits VK_SPARSE_MEMORY_BIND_METADATA_BIT = new VkSparseMemoryBindFlagBits(1);

    public static VkSparseMemoryBindFlagBits fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_SPARSE_MEMORY_BIND_FLAG_BITS_MAX_ENUM;
            case 1 -> VK_SPARSE_MEMORY_BIND_METADATA_BIT;
            default -> new VkSparseMemoryBindFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
