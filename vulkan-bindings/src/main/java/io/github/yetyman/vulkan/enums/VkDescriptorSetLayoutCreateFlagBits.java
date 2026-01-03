package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkDescriptorSetLayoutCreateFlagBits
 * Generated from jextract bindings
 */
public record VkDescriptorSetLayoutCreateFlagBits(int value) {

    public static final VkDescriptorSetLayoutCreateFlagBits VK_DESCRIPTOR_SET_LAYOUT_CREATE_DESCRIPTOR_BUFFER_BIT_EXT = new VkDescriptorSetLayoutCreateFlagBits(16);
    public static final VkDescriptorSetLayoutCreateFlagBits VK_DESCRIPTOR_SET_LAYOUT_CREATE_EMBEDDED_IMMUTABLE_SAMPLERS_BIT_EXT = new VkDescriptorSetLayoutCreateFlagBits(32);
    public static final VkDescriptorSetLayoutCreateFlagBits VK_DESCRIPTOR_SET_LAYOUT_CREATE_FLAG_BITS_MAX_ENUM = new VkDescriptorSetLayoutCreateFlagBits(2147483647);
    public static final VkDescriptorSetLayoutCreateFlagBits VK_DESCRIPTOR_SET_LAYOUT_CREATE_HOST_ONLY_POOL_BIT_EXT = new VkDescriptorSetLayoutCreateFlagBits(4);
    public static final VkDescriptorSetLayoutCreateFlagBits VK_DESCRIPTOR_SET_LAYOUT_CREATE_HOST_ONLY_POOL_BIT_VALVE = VK_DESCRIPTOR_SET_LAYOUT_CREATE_HOST_ONLY_POOL_BIT_EXT;
    public static final VkDescriptorSetLayoutCreateFlagBits VK_DESCRIPTOR_SET_LAYOUT_CREATE_INDIRECT_BINDABLE_BIT_NV = new VkDescriptorSetLayoutCreateFlagBits(128);
    public static final VkDescriptorSetLayoutCreateFlagBits VK_DESCRIPTOR_SET_LAYOUT_CREATE_PER_STAGE_BIT_NV = new VkDescriptorSetLayoutCreateFlagBits(64);
    public static final VkDescriptorSetLayoutCreateFlagBits VK_DESCRIPTOR_SET_LAYOUT_CREATE_PUSH_DESCRIPTOR_BIT = new VkDescriptorSetLayoutCreateFlagBits(1);
    public static final VkDescriptorSetLayoutCreateFlagBits VK_DESCRIPTOR_SET_LAYOUT_CREATE_PUSH_DESCRIPTOR_BIT_KHR = VK_DESCRIPTOR_SET_LAYOUT_CREATE_PUSH_DESCRIPTOR_BIT;
    public static final VkDescriptorSetLayoutCreateFlagBits VK_DESCRIPTOR_SET_LAYOUT_CREATE_UPDATE_AFTER_BIND_POOL_BIT = new VkDescriptorSetLayoutCreateFlagBits(2);
    public static final VkDescriptorSetLayoutCreateFlagBits VK_DESCRIPTOR_SET_LAYOUT_CREATE_UPDATE_AFTER_BIND_POOL_BIT_EXT = VK_DESCRIPTOR_SET_LAYOUT_CREATE_UPDATE_AFTER_BIND_POOL_BIT;

    public static VkDescriptorSetLayoutCreateFlagBits fromValue(int value) {
        return switch (value) {
            case 16 -> VK_DESCRIPTOR_SET_LAYOUT_CREATE_DESCRIPTOR_BUFFER_BIT_EXT;
            case 32 -> VK_DESCRIPTOR_SET_LAYOUT_CREATE_EMBEDDED_IMMUTABLE_SAMPLERS_BIT_EXT;
            case 2147483647 -> VK_DESCRIPTOR_SET_LAYOUT_CREATE_FLAG_BITS_MAX_ENUM;
            case 4 -> VK_DESCRIPTOR_SET_LAYOUT_CREATE_HOST_ONLY_POOL_BIT_VALVE;
            case 128 -> VK_DESCRIPTOR_SET_LAYOUT_CREATE_INDIRECT_BINDABLE_BIT_NV;
            case 64 -> VK_DESCRIPTOR_SET_LAYOUT_CREATE_PER_STAGE_BIT_NV;
            case 1 -> VK_DESCRIPTOR_SET_LAYOUT_CREATE_PUSH_DESCRIPTOR_BIT;
            case 2 -> VK_DESCRIPTOR_SET_LAYOUT_CREATE_UPDATE_AFTER_BIND_POOL_BIT;
            default -> new VkDescriptorSetLayoutCreateFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
