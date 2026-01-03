package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkDebugUtilsMessageSeverityFlagBitsEXT
 * Generated from jextract bindings
 */
public record VkDebugUtilsMessageSeverityFlagBitsEXT(int value) {

    public static final VkDebugUtilsMessageSeverityFlagBitsEXT VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT = new VkDebugUtilsMessageSeverityFlagBitsEXT(4096);
    public static final VkDebugUtilsMessageSeverityFlagBitsEXT VK_DEBUG_UTILS_MESSAGE_SEVERITY_FLAG_BITS_MAX_ENUM_EXT = new VkDebugUtilsMessageSeverityFlagBitsEXT(2147483647);
    public static final VkDebugUtilsMessageSeverityFlagBitsEXT VK_DEBUG_UTILS_MESSAGE_SEVERITY_INFO_BIT_EXT = new VkDebugUtilsMessageSeverityFlagBitsEXT(16);
    public static final VkDebugUtilsMessageSeverityFlagBitsEXT VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT = new VkDebugUtilsMessageSeverityFlagBitsEXT(1);
    public static final VkDebugUtilsMessageSeverityFlagBitsEXT VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT = new VkDebugUtilsMessageSeverityFlagBitsEXT(256);

    public static VkDebugUtilsMessageSeverityFlagBitsEXT fromValue(int value) {
        return switch (value) {
            case 4096 -> VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT;
            case 2147483647 -> VK_DEBUG_UTILS_MESSAGE_SEVERITY_FLAG_BITS_MAX_ENUM_EXT;
            case 16 -> VK_DEBUG_UTILS_MESSAGE_SEVERITY_INFO_BIT_EXT;
            case 1 -> VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT;
            case 256 -> VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT;
            default -> new VkDebugUtilsMessageSeverityFlagBitsEXT(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
