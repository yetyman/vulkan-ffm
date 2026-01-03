package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkSwapchainCreateFlagBitsKHR
 * Generated from jextract bindings
 */
public record VkSwapchainCreateFlagBitsKHR(int value) {

    public static final VkSwapchainCreateFlagBitsKHR VK_SWAPCHAIN_CREATE_DEFERRED_MEMORY_ALLOCATION_BIT_EXT = new VkSwapchainCreateFlagBitsKHR(8);
    public static final VkSwapchainCreateFlagBitsKHR VK_SWAPCHAIN_CREATE_DEFERRED_MEMORY_ALLOCATION_BIT_KHR = VK_SWAPCHAIN_CREATE_DEFERRED_MEMORY_ALLOCATION_BIT_EXT;
    public static final VkSwapchainCreateFlagBitsKHR VK_SWAPCHAIN_CREATE_FLAG_BITS_MAX_ENUM_KHR = new VkSwapchainCreateFlagBitsKHR(2147483647);
    public static final VkSwapchainCreateFlagBitsKHR VK_SWAPCHAIN_CREATE_MUTABLE_FORMAT_BIT_KHR = new VkSwapchainCreateFlagBitsKHR(4);
    public static final VkSwapchainCreateFlagBitsKHR VK_SWAPCHAIN_CREATE_PRESENT_ID_2_BIT_KHR = new VkSwapchainCreateFlagBitsKHR(64);
    public static final VkSwapchainCreateFlagBitsKHR VK_SWAPCHAIN_CREATE_PRESENT_TIMING_BIT_EXT = new VkSwapchainCreateFlagBitsKHR(512);
    public static final VkSwapchainCreateFlagBitsKHR VK_SWAPCHAIN_CREATE_PRESENT_WAIT_2_BIT_KHR = new VkSwapchainCreateFlagBitsKHR(128);
    public static final VkSwapchainCreateFlagBitsKHR VK_SWAPCHAIN_CREATE_PROTECTED_BIT_KHR = new VkSwapchainCreateFlagBitsKHR(2);
    public static final VkSwapchainCreateFlagBitsKHR VK_SWAPCHAIN_CREATE_SPLIT_INSTANCE_BIND_REGIONS_BIT_KHR = new VkSwapchainCreateFlagBitsKHR(1);

    public static VkSwapchainCreateFlagBitsKHR fromValue(int value) {
        return switch (value) {
            case 8 -> VK_SWAPCHAIN_CREATE_DEFERRED_MEMORY_ALLOCATION_BIT_EXT;
            case 2147483647 -> VK_SWAPCHAIN_CREATE_FLAG_BITS_MAX_ENUM_KHR;
            case 4 -> VK_SWAPCHAIN_CREATE_MUTABLE_FORMAT_BIT_KHR;
            case 64 -> VK_SWAPCHAIN_CREATE_PRESENT_ID_2_BIT_KHR;
            case 512 -> VK_SWAPCHAIN_CREATE_PRESENT_TIMING_BIT_EXT;
            case 128 -> VK_SWAPCHAIN_CREATE_PRESENT_WAIT_2_BIT_KHR;
            case 2 -> VK_SWAPCHAIN_CREATE_PROTECTED_BIT_KHR;
            case 1 -> VK_SWAPCHAIN_CREATE_SPLIT_INSTANCE_BIND_REGIONS_BIT_KHR;
            default -> new VkSwapchainCreateFlagBitsKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
