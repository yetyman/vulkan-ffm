package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkDeviceMemoryReportEventTypeEXT
 * Generated from jextract bindings
 */
public record VkDeviceMemoryReportEventTypeEXT(int value) {

    public static final VkDeviceMemoryReportEventTypeEXT VK_DEVICE_MEMORY_REPORT_EVENT_TYPE_ALLOCATE_EXT = new VkDeviceMemoryReportEventTypeEXT(0);
    public static final VkDeviceMemoryReportEventTypeEXT VK_DEVICE_MEMORY_REPORT_EVENT_TYPE_ALLOCATION_FAILED_EXT = new VkDeviceMemoryReportEventTypeEXT(4);
    public static final VkDeviceMemoryReportEventTypeEXT VK_DEVICE_MEMORY_REPORT_EVENT_TYPE_FREE_EXT = new VkDeviceMemoryReportEventTypeEXT(1);
    public static final VkDeviceMemoryReportEventTypeEXT VK_DEVICE_MEMORY_REPORT_EVENT_TYPE_IMPORT_EXT = new VkDeviceMemoryReportEventTypeEXT(2);
    public static final VkDeviceMemoryReportEventTypeEXT VK_DEVICE_MEMORY_REPORT_EVENT_TYPE_MAX_ENUM_EXT = new VkDeviceMemoryReportEventTypeEXT(2147483647);
    public static final VkDeviceMemoryReportEventTypeEXT VK_DEVICE_MEMORY_REPORT_EVENT_TYPE_UNIMPORT_EXT = new VkDeviceMemoryReportEventTypeEXT(3);

    public static VkDeviceMemoryReportEventTypeEXT fromValue(int value) {
        return switch (value) {
            case 0 -> VK_DEVICE_MEMORY_REPORT_EVENT_TYPE_ALLOCATE_EXT;
            case 4 -> VK_DEVICE_MEMORY_REPORT_EVENT_TYPE_ALLOCATION_FAILED_EXT;
            case 1 -> VK_DEVICE_MEMORY_REPORT_EVENT_TYPE_FREE_EXT;
            case 2 -> VK_DEVICE_MEMORY_REPORT_EVENT_TYPE_IMPORT_EXT;
            case 2147483647 -> VK_DEVICE_MEMORY_REPORT_EVENT_TYPE_MAX_ENUM_EXT;
            case 3 -> VK_DEVICE_MEMORY_REPORT_EVENT_TYPE_UNIMPORT_EXT;
            default -> new VkDeviceMemoryReportEventTypeEXT(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
