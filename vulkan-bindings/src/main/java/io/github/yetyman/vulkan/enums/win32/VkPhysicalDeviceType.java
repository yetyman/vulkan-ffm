package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkPhysicalDeviceType
 * Generated from jextract bindings
 */
public record VkPhysicalDeviceType(int value) {

    public static final VkPhysicalDeviceType VK_PHYSICAL_DEVICE_TYPE_CPU = new VkPhysicalDeviceType(4);
    public static final VkPhysicalDeviceType VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU = new VkPhysicalDeviceType(2);
    public static final VkPhysicalDeviceType VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU = new VkPhysicalDeviceType(1);
    public static final VkPhysicalDeviceType VK_PHYSICAL_DEVICE_TYPE_MAX_ENUM = new VkPhysicalDeviceType(2147483647);
    public static final VkPhysicalDeviceType VK_PHYSICAL_DEVICE_TYPE_OTHER = new VkPhysicalDeviceType(0);
    public static final VkPhysicalDeviceType VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU = new VkPhysicalDeviceType(3);

    public static VkPhysicalDeviceType fromValue(int value) {
        return switch (value) {
            case 4 -> VK_PHYSICAL_DEVICE_TYPE_CPU;
            case 2 -> VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU;
            case 1 -> VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU;
            case 2147483647 -> VK_PHYSICAL_DEVICE_TYPE_MAX_ENUM;
            case 0 -> VK_PHYSICAL_DEVICE_TYPE_OTHER;
            case 3 -> VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU;
            default -> new VkPhysicalDeviceType(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
