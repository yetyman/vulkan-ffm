package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkDeviceGroupPresentModeFlagBitsKHR
 * Generated from jextract bindings
 */
public record VkDeviceGroupPresentModeFlagBitsKHR(int value) {

    public static final VkDeviceGroupPresentModeFlagBitsKHR VK_DEVICE_GROUP_PRESENT_MODE_FLAG_BITS_MAX_ENUM_KHR = new VkDeviceGroupPresentModeFlagBitsKHR(2147483647);
    public static final VkDeviceGroupPresentModeFlagBitsKHR VK_DEVICE_GROUP_PRESENT_MODE_LOCAL_BIT_KHR = new VkDeviceGroupPresentModeFlagBitsKHR(1);
    public static final VkDeviceGroupPresentModeFlagBitsKHR VK_DEVICE_GROUP_PRESENT_MODE_LOCAL_MULTI_DEVICE_BIT_KHR = new VkDeviceGroupPresentModeFlagBitsKHR(8);
    public static final VkDeviceGroupPresentModeFlagBitsKHR VK_DEVICE_GROUP_PRESENT_MODE_REMOTE_BIT_KHR = new VkDeviceGroupPresentModeFlagBitsKHR(2);
    public static final VkDeviceGroupPresentModeFlagBitsKHR VK_DEVICE_GROUP_PRESENT_MODE_SUM_BIT_KHR = new VkDeviceGroupPresentModeFlagBitsKHR(4);

    public static VkDeviceGroupPresentModeFlagBitsKHR fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_DEVICE_GROUP_PRESENT_MODE_FLAG_BITS_MAX_ENUM_KHR;
            case 1 -> VK_DEVICE_GROUP_PRESENT_MODE_LOCAL_BIT_KHR;
            case 8 -> VK_DEVICE_GROUP_PRESENT_MODE_LOCAL_MULTI_DEVICE_BIT_KHR;
            case 2 -> VK_DEVICE_GROUP_PRESENT_MODE_REMOTE_BIT_KHR;
            case 4 -> VK_DEVICE_GROUP_PRESENT_MODE_SUM_BIT_KHR;
            default -> new VkDeviceGroupPresentModeFlagBitsKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
