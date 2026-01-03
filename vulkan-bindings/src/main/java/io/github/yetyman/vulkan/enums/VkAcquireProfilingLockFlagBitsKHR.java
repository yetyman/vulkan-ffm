package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkAcquireProfilingLockFlagBitsKHR
 * Generated from jextract bindings
 */
public record VkAcquireProfilingLockFlagBitsKHR(int value) {

    public static final VkAcquireProfilingLockFlagBitsKHR VK_ACQUIRE_PROFILING_LOCK_FLAG_BITS_MAX_ENUM_KHR = new VkAcquireProfilingLockFlagBitsKHR(2147483647);

    public static VkAcquireProfilingLockFlagBitsKHR fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_ACQUIRE_PROFILING_LOCK_FLAG_BITS_MAX_ENUM_KHR;
            default -> new VkAcquireProfilingLockFlagBitsKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
