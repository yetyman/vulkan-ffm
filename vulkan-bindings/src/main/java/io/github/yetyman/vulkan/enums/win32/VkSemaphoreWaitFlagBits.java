package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkSemaphoreWaitFlagBits
 * Generated from jextract bindings
 */
public record VkSemaphoreWaitFlagBits(int value) {

    public static final VkSemaphoreWaitFlagBits VK_SEMAPHORE_WAIT_ANY_BIT = new VkSemaphoreWaitFlagBits(1);
    public static final VkSemaphoreWaitFlagBits VK_SEMAPHORE_WAIT_ANY_BIT_KHR = new VkSemaphoreWaitFlagBits(1);
    public static final VkSemaphoreWaitFlagBits VK_SEMAPHORE_WAIT_FLAG_BITS_MAX_ENUM = new VkSemaphoreWaitFlagBits(2147483647);

    public static VkSemaphoreWaitFlagBits fromValue(int value) {
        return switch (value) {
            case 1 -> VK_SEMAPHORE_WAIT_ANY_BIT;
            case 2147483647 -> VK_SEMAPHORE_WAIT_FLAG_BITS_MAX_ENUM;
            default -> new VkSemaphoreWaitFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
