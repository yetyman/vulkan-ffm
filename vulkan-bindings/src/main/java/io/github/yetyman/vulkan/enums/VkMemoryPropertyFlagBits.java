package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkMemoryPropertyFlagBits
 * Generated from jextract bindings
 */
public record VkMemoryPropertyFlagBits(int value) {

    public static final VkMemoryPropertyFlagBits VK_MEMORY_PROPERTY_DEVICE_COHERENT_BIT_AMD = new VkMemoryPropertyFlagBits(64);
    public static final VkMemoryPropertyFlagBits VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT = new VkMemoryPropertyFlagBits(1);
    public static final VkMemoryPropertyFlagBits VK_MEMORY_PROPERTY_DEVICE_UNCACHED_BIT_AMD = new VkMemoryPropertyFlagBits(128);
    public static final VkMemoryPropertyFlagBits VK_MEMORY_PROPERTY_FLAG_BITS_MAX_ENUM = new VkMemoryPropertyFlagBits(2147483647);
    public static final VkMemoryPropertyFlagBits VK_MEMORY_PROPERTY_HOST_CACHED_BIT = new VkMemoryPropertyFlagBits(8);
    public static final VkMemoryPropertyFlagBits VK_MEMORY_PROPERTY_HOST_COHERENT_BIT = new VkMemoryPropertyFlagBits(4);
    public static final VkMemoryPropertyFlagBits VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT = new VkMemoryPropertyFlagBits(2);
    public static final VkMemoryPropertyFlagBits VK_MEMORY_PROPERTY_LAZILY_ALLOCATED_BIT = new VkMemoryPropertyFlagBits(16);
    public static final VkMemoryPropertyFlagBits VK_MEMORY_PROPERTY_PROTECTED_BIT = new VkMemoryPropertyFlagBits(32);
    public static final VkMemoryPropertyFlagBits VK_MEMORY_PROPERTY_RDMA_CAPABLE_BIT_NV = new VkMemoryPropertyFlagBits(256);

    public static VkMemoryPropertyFlagBits fromValue(int value) {
        return switch (value) {
            case 64 -> VK_MEMORY_PROPERTY_DEVICE_COHERENT_BIT_AMD;
            case 1 -> VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;
            case 128 -> VK_MEMORY_PROPERTY_DEVICE_UNCACHED_BIT_AMD;
            case 2147483647 -> VK_MEMORY_PROPERTY_FLAG_BITS_MAX_ENUM;
            case 8 -> VK_MEMORY_PROPERTY_HOST_CACHED_BIT;
            case 4 -> VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
            case 2 -> VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT;
            case 16 -> VK_MEMORY_PROPERTY_LAZILY_ALLOCATED_BIT;
            case 32 -> VK_MEMORY_PROPERTY_PROTECTED_BIT;
            case 256 -> VK_MEMORY_PROPERTY_RDMA_CAPABLE_BIT_NV;
            default -> new VkMemoryPropertyFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
