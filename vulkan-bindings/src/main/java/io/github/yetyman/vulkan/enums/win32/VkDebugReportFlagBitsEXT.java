package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkDebugReportFlagBitsEXT
 * Generated from jextract bindings
 */
public record VkDebugReportFlagBitsEXT(int value) {

    public static final VkDebugReportFlagBitsEXT VK_DEBUG_REPORT_DEBUG_BIT_EXT = new VkDebugReportFlagBitsEXT(16);
    public static final VkDebugReportFlagBitsEXT VK_DEBUG_REPORT_ERROR_BIT_EXT = new VkDebugReportFlagBitsEXT(8);
    public static final VkDebugReportFlagBitsEXT VK_DEBUG_REPORT_FLAG_BITS_MAX_ENUM_EXT = new VkDebugReportFlagBitsEXT(2147483647);
    public static final VkDebugReportFlagBitsEXT VK_DEBUG_REPORT_INFORMATION_BIT_EXT = new VkDebugReportFlagBitsEXT(1);
    public static final VkDebugReportFlagBitsEXT VK_DEBUG_REPORT_PERFORMANCE_WARNING_BIT_EXT = new VkDebugReportFlagBitsEXT(4);
    public static final VkDebugReportFlagBitsEXT VK_DEBUG_REPORT_WARNING_BIT_EXT = new VkDebugReportFlagBitsEXT(2);

    public static VkDebugReportFlagBitsEXT fromValue(int value) {
        return switch (value) {
            case 16 -> VK_DEBUG_REPORT_DEBUG_BIT_EXT;
            case 8 -> VK_DEBUG_REPORT_ERROR_BIT_EXT;
            case 2147483647 -> VK_DEBUG_REPORT_FLAG_BITS_MAX_ENUM_EXT;
            case 1 -> VK_DEBUG_REPORT_INFORMATION_BIT_EXT;
            case 4 -> VK_DEBUG_REPORT_PERFORMANCE_WARNING_BIT_EXT;
            case 2 -> VK_DEBUG_REPORT_WARNING_BIT_EXT;
            default -> new VkDebugReportFlagBitsEXT(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
