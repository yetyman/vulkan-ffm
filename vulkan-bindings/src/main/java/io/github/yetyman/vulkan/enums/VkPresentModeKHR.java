package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkPresentModeKHR
 * Generated from jextract bindings
 */
public record VkPresentModeKHR(int value) {

    public static final VkPresentModeKHR VK_PRESENT_MODE_FIFO_KHR = new VkPresentModeKHR(2);
    public static final VkPresentModeKHR VK_PRESENT_MODE_FIFO_LATEST_READY_EXT = new VkPresentModeKHR(1000361000);
    public static final VkPresentModeKHR VK_PRESENT_MODE_FIFO_LATEST_READY_KHR = VK_PRESENT_MODE_FIFO_LATEST_READY_EXT;
    public static final VkPresentModeKHR VK_PRESENT_MODE_FIFO_RELAXED_KHR = new VkPresentModeKHR(3);
    public static final VkPresentModeKHR VK_PRESENT_MODE_IMMEDIATE_KHR = new VkPresentModeKHR(0);
    public static final VkPresentModeKHR VK_PRESENT_MODE_MAILBOX_KHR = new VkPresentModeKHR(1);
    public static final VkPresentModeKHR VK_PRESENT_MODE_MAX_ENUM_KHR = new VkPresentModeKHR(2147483647);
    public static final VkPresentModeKHR VK_PRESENT_MODE_SHARED_CONTINUOUS_REFRESH_KHR = new VkPresentModeKHR(1000111001);
    public static final VkPresentModeKHR VK_PRESENT_MODE_SHARED_DEMAND_REFRESH_KHR = new VkPresentModeKHR(1000111000);

    public static VkPresentModeKHR fromValue(int value) {
        return switch (value) {
            case 2 -> VK_PRESENT_MODE_FIFO_KHR;
            case 1000361000 -> VK_PRESENT_MODE_FIFO_LATEST_READY_EXT;
            case 3 -> VK_PRESENT_MODE_FIFO_RELAXED_KHR;
            case 0 -> VK_PRESENT_MODE_IMMEDIATE_KHR;
            case 1 -> VK_PRESENT_MODE_MAILBOX_KHR;
            case 2147483647 -> VK_PRESENT_MODE_MAX_ENUM_KHR;
            case 1000111001 -> VK_PRESENT_MODE_SHARED_CONTINUOUS_REFRESH_KHR;
            case 1000111000 -> VK_PRESENT_MODE_SHARED_DEMAND_REFRESH_KHR;
            default -> new VkPresentModeKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
