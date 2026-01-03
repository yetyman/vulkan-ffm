package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkDebugUtilsMessageTypeFlagBitsEXT
 * Generated from jextract bindings
 */
public record VkDebugUtilsMessageTypeFlagBitsEXT(int value) {

    public static final VkDebugUtilsMessageTypeFlagBitsEXT VK_DEBUG_UTILS_MESSAGE_TYPE_DEVICE_ADDRESS_BINDING_BIT_EXT = new VkDebugUtilsMessageTypeFlagBitsEXT(8);
    public static final VkDebugUtilsMessageTypeFlagBitsEXT VK_DEBUG_UTILS_MESSAGE_TYPE_FLAG_BITS_MAX_ENUM_EXT = new VkDebugUtilsMessageTypeFlagBitsEXT(2147483647);
    public static final VkDebugUtilsMessageTypeFlagBitsEXT VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT = new VkDebugUtilsMessageTypeFlagBitsEXT(1);
    public static final VkDebugUtilsMessageTypeFlagBitsEXT VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT = new VkDebugUtilsMessageTypeFlagBitsEXT(4);
    public static final VkDebugUtilsMessageTypeFlagBitsEXT VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT = new VkDebugUtilsMessageTypeFlagBitsEXT(2);

    public static VkDebugUtilsMessageTypeFlagBitsEXT fromValue(int value) {
        return switch (value) {
            case 8 -> VK_DEBUG_UTILS_MESSAGE_TYPE_DEVICE_ADDRESS_BINDING_BIT_EXT;
            case 2147483647 -> VK_DEBUG_UTILS_MESSAGE_TYPE_FLAG_BITS_MAX_ENUM_EXT;
            case 1 -> VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT;
            case 4 -> VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT;
            case 2 -> VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT;
            default -> new VkDebugUtilsMessageTypeFlagBitsEXT(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
