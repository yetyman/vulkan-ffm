package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkDescriptorBindingFlagBits
 * Generated from jextract bindings
 */
public record VkDescriptorBindingFlagBits(int value) {

    public static final VkDescriptorBindingFlagBits VK_DESCRIPTOR_BINDING_FLAG_BITS_MAX_ENUM = new VkDescriptorBindingFlagBits(2147483647);
    public static final VkDescriptorBindingFlagBits VK_DESCRIPTOR_BINDING_PARTIALLY_BOUND_BIT = new VkDescriptorBindingFlagBits(4);
    public static final VkDescriptorBindingFlagBits VK_DESCRIPTOR_BINDING_PARTIALLY_BOUND_BIT_EXT = VK_DESCRIPTOR_BINDING_PARTIALLY_BOUND_BIT;
    public static final VkDescriptorBindingFlagBits VK_DESCRIPTOR_BINDING_UPDATE_AFTER_BIND_BIT = new VkDescriptorBindingFlagBits(1);
    public static final VkDescriptorBindingFlagBits VK_DESCRIPTOR_BINDING_UPDATE_AFTER_BIND_BIT_EXT = VK_DESCRIPTOR_BINDING_UPDATE_AFTER_BIND_BIT;
    public static final VkDescriptorBindingFlagBits VK_DESCRIPTOR_BINDING_UPDATE_UNUSED_WHILE_PENDING_BIT = new VkDescriptorBindingFlagBits(2);
    public static final VkDescriptorBindingFlagBits VK_DESCRIPTOR_BINDING_UPDATE_UNUSED_WHILE_PENDING_BIT_EXT = VK_DESCRIPTOR_BINDING_UPDATE_UNUSED_WHILE_PENDING_BIT;
    public static final VkDescriptorBindingFlagBits VK_DESCRIPTOR_BINDING_VARIABLE_DESCRIPTOR_COUNT_BIT = new VkDescriptorBindingFlagBits(8);
    public static final VkDescriptorBindingFlagBits VK_DESCRIPTOR_BINDING_VARIABLE_DESCRIPTOR_COUNT_BIT_EXT = VK_DESCRIPTOR_BINDING_VARIABLE_DESCRIPTOR_COUNT_BIT;

    public static VkDescriptorBindingFlagBits fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_DESCRIPTOR_BINDING_FLAG_BITS_MAX_ENUM;
            case 4 -> VK_DESCRIPTOR_BINDING_PARTIALLY_BOUND_BIT;
            case 1 -> VK_DESCRIPTOR_BINDING_UPDATE_AFTER_BIND_BIT;
            case 2 -> VK_DESCRIPTOR_BINDING_UPDATE_UNUSED_WHILE_PENDING_BIT;
            case 8 -> VK_DESCRIPTOR_BINDING_VARIABLE_DESCRIPTOR_COUNT_BIT;
            default -> new VkDescriptorBindingFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
