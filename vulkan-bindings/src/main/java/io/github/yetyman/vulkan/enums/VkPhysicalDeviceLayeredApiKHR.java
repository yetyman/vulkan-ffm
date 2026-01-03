package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkPhysicalDeviceLayeredApiKHR
 * Generated from jextract bindings
 */
public record VkPhysicalDeviceLayeredApiKHR(int value) {

    public static final VkPhysicalDeviceLayeredApiKHR VK_PHYSICAL_DEVICE_LAYERED_API_D3D12_KHR = new VkPhysicalDeviceLayeredApiKHR(1);
    public static final VkPhysicalDeviceLayeredApiKHR VK_PHYSICAL_DEVICE_LAYERED_API_MAX_ENUM_KHR = new VkPhysicalDeviceLayeredApiKHR(2147483647);
    public static final VkPhysicalDeviceLayeredApiKHR VK_PHYSICAL_DEVICE_LAYERED_API_METAL_KHR = new VkPhysicalDeviceLayeredApiKHR(2);
    public static final VkPhysicalDeviceLayeredApiKHR VK_PHYSICAL_DEVICE_LAYERED_API_OPENGLES_KHR = new VkPhysicalDeviceLayeredApiKHR(4);
    public static final VkPhysicalDeviceLayeredApiKHR VK_PHYSICAL_DEVICE_LAYERED_API_OPENGL_KHR = new VkPhysicalDeviceLayeredApiKHR(3);
    public static final VkPhysicalDeviceLayeredApiKHR VK_PHYSICAL_DEVICE_LAYERED_API_VULKAN_KHR = new VkPhysicalDeviceLayeredApiKHR(0);

    public static VkPhysicalDeviceLayeredApiKHR fromValue(int value) {
        return switch (value) {
            case 1 -> VK_PHYSICAL_DEVICE_LAYERED_API_D3D12_KHR;
            case 2147483647 -> VK_PHYSICAL_DEVICE_LAYERED_API_MAX_ENUM_KHR;
            case 2 -> VK_PHYSICAL_DEVICE_LAYERED_API_METAL_KHR;
            case 4 -> VK_PHYSICAL_DEVICE_LAYERED_API_OPENGLES_KHR;
            case 3 -> VK_PHYSICAL_DEVICE_LAYERED_API_OPENGL_KHR;
            case 0 -> VK_PHYSICAL_DEVICE_LAYERED_API_VULKAN_KHR;
            default -> new VkPhysicalDeviceLayeredApiKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
