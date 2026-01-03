package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkSemaphoreType
 * Generated from jextract bindings
 */
public record VkSemaphoreType(int value) {

    public static final VkSemaphoreType VK_SEMAPHORE_TYPE_BINARY = new VkSemaphoreType(0);
    public static final VkSemaphoreType VK_SEMAPHORE_TYPE_BINARY_KHR = new VkSemaphoreType(0);
    public static final VkSemaphoreType VK_SEMAPHORE_TYPE_MAX_ENUM = new VkSemaphoreType(2147483647);
    public static final VkSemaphoreType VK_SEMAPHORE_TYPE_TIMELINE = new VkSemaphoreType(1);
    public static final VkSemaphoreType VK_SEMAPHORE_TYPE_TIMELINE_KHR = new VkSemaphoreType(1);

    public static VkSemaphoreType fromValue(int value) {
        return switch (value) {
            case 0 -> VK_SEMAPHORE_TYPE_BINARY;
            case 2147483647 -> VK_SEMAPHORE_TYPE_MAX_ENUM;
            case 1 -> VK_SEMAPHORE_TYPE_TIMELINE;
            default -> new VkSemaphoreType(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
