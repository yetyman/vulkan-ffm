package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkDescriptorPoolCreateFlagBits
 * Generated from jextract bindings
 */
public record VkDescriptorPoolCreateFlagBits(int value) {

    public static final VkDescriptorPoolCreateFlagBits VK_DESCRIPTOR_POOL_CREATE_ALLOW_OVERALLOCATION_POOLS_BIT_NV = new VkDescriptorPoolCreateFlagBits(16);
    public static final VkDescriptorPoolCreateFlagBits VK_DESCRIPTOR_POOL_CREATE_ALLOW_OVERALLOCATION_SETS_BIT_NV = new VkDescriptorPoolCreateFlagBits(8);
    public static final VkDescriptorPoolCreateFlagBits VK_DESCRIPTOR_POOL_CREATE_FLAG_BITS_MAX_ENUM = new VkDescriptorPoolCreateFlagBits(2147483647);
    public static final VkDescriptorPoolCreateFlagBits VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT = new VkDescriptorPoolCreateFlagBits(1);
    public static final VkDescriptorPoolCreateFlagBits VK_DESCRIPTOR_POOL_CREATE_HOST_ONLY_BIT_EXT = new VkDescriptorPoolCreateFlagBits(4);
    public static final VkDescriptorPoolCreateFlagBits VK_DESCRIPTOR_POOL_CREATE_HOST_ONLY_BIT_VALVE = new VkDescriptorPoolCreateFlagBits(4);
    public static final VkDescriptorPoolCreateFlagBits VK_DESCRIPTOR_POOL_CREATE_UPDATE_AFTER_BIND_BIT = new VkDescriptorPoolCreateFlagBits(2);
    public static final VkDescriptorPoolCreateFlagBits VK_DESCRIPTOR_POOL_CREATE_UPDATE_AFTER_BIND_BIT_EXT = new VkDescriptorPoolCreateFlagBits(2);

    public static VkDescriptorPoolCreateFlagBits fromValue(int value) {
        return switch (value) {
            case 16 -> VK_DESCRIPTOR_POOL_CREATE_ALLOW_OVERALLOCATION_POOLS_BIT_NV;
            case 8 -> VK_DESCRIPTOR_POOL_CREATE_ALLOW_OVERALLOCATION_SETS_BIT_NV;
            case 2147483647 -> VK_DESCRIPTOR_POOL_CREATE_FLAG_BITS_MAX_ENUM;
            case 1 -> VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT;
            case 4 -> VK_DESCRIPTOR_POOL_CREATE_HOST_ONLY_BIT_VALVE;
            case 2 -> VK_DESCRIPTOR_POOL_CREATE_UPDATE_AFTER_BIND_BIT;
            default -> new VkDescriptorPoolCreateFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
