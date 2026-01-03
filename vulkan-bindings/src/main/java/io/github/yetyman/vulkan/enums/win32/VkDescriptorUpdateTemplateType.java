package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkDescriptorUpdateTemplateType
 * Generated from jextract bindings
 */
public record VkDescriptorUpdateTemplateType(int value) {

    public static final VkDescriptorUpdateTemplateType VK_DESCRIPTOR_UPDATE_TEMPLATE_TYPE_DESCRIPTOR_SET = new VkDescriptorUpdateTemplateType(0);
    public static final VkDescriptorUpdateTemplateType VK_DESCRIPTOR_UPDATE_TEMPLATE_TYPE_DESCRIPTOR_SET_KHR = new VkDescriptorUpdateTemplateType(0);
    public static final VkDescriptorUpdateTemplateType VK_DESCRIPTOR_UPDATE_TEMPLATE_TYPE_MAX_ENUM = new VkDescriptorUpdateTemplateType(2147483647);
    public static final VkDescriptorUpdateTemplateType VK_DESCRIPTOR_UPDATE_TEMPLATE_TYPE_PUSH_DESCRIPTORS = new VkDescriptorUpdateTemplateType(1);
    public static final VkDescriptorUpdateTemplateType VK_DESCRIPTOR_UPDATE_TEMPLATE_TYPE_PUSH_DESCRIPTORS_KHR = new VkDescriptorUpdateTemplateType(1);

    public static VkDescriptorUpdateTemplateType fromValue(int value) {
        return switch (value) {
            case 0 -> VK_DESCRIPTOR_UPDATE_TEMPLATE_TYPE_DESCRIPTOR_SET;
            case 2147483647 -> VK_DESCRIPTOR_UPDATE_TEMPLATE_TYPE_MAX_ENUM;
            case 1 -> VK_DESCRIPTOR_UPDATE_TEMPLATE_TYPE_PUSH_DESCRIPTORS;
            default -> new VkDescriptorUpdateTemplateType(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
