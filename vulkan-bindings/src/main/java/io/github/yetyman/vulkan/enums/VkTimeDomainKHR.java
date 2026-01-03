package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkTimeDomainKHR
 * Generated from jextract bindings
 */
public record VkTimeDomainKHR(int value) {

    public static final VkTimeDomainKHR VK_TIME_DOMAIN_CLOCK_MONOTONIC_EXT = new VkTimeDomainKHR(1);
    public static final VkTimeDomainKHR VK_TIME_DOMAIN_CLOCK_MONOTONIC_KHR = VK_TIME_DOMAIN_CLOCK_MONOTONIC_EXT;
    public static final VkTimeDomainKHR VK_TIME_DOMAIN_CLOCK_MONOTONIC_RAW_EXT = new VkTimeDomainKHR(2);
    public static final VkTimeDomainKHR VK_TIME_DOMAIN_CLOCK_MONOTONIC_RAW_KHR = VK_TIME_DOMAIN_CLOCK_MONOTONIC_RAW_EXT;
    public static final VkTimeDomainKHR VK_TIME_DOMAIN_DEVICE_EXT = new VkTimeDomainKHR(0);
    public static final VkTimeDomainKHR VK_TIME_DOMAIN_DEVICE_KHR = VK_TIME_DOMAIN_DEVICE_EXT;
    public static final VkTimeDomainKHR VK_TIME_DOMAIN_MAX_ENUM_KHR = new VkTimeDomainKHR(2147483647);
    public static final VkTimeDomainKHR VK_TIME_DOMAIN_PRESENT_STAGE_LOCAL_EXT = new VkTimeDomainKHR(1000208000);
    public static final VkTimeDomainKHR VK_TIME_DOMAIN_QUERY_PERFORMANCE_COUNTER_EXT = new VkTimeDomainKHR(3);
    public static final VkTimeDomainKHR VK_TIME_DOMAIN_QUERY_PERFORMANCE_COUNTER_KHR = VK_TIME_DOMAIN_QUERY_PERFORMANCE_COUNTER_EXT;
    public static final VkTimeDomainKHR VK_TIME_DOMAIN_SWAPCHAIN_LOCAL_EXT = new VkTimeDomainKHR(1000208001);

    public static VkTimeDomainKHR fromValue(int value) {
        return switch (value) {
            case 1 -> VK_TIME_DOMAIN_CLOCK_MONOTONIC_EXT;
            case 2 -> VK_TIME_DOMAIN_CLOCK_MONOTONIC_RAW_EXT;
            case 0 -> VK_TIME_DOMAIN_DEVICE_EXT;
            case 2147483647 -> VK_TIME_DOMAIN_MAX_ENUM_KHR;
            case 1000208000 -> VK_TIME_DOMAIN_PRESENT_STAGE_LOCAL_EXT;
            case 3 -> VK_TIME_DOMAIN_QUERY_PERFORMANCE_COUNTER_EXT;
            case 1000208001 -> VK_TIME_DOMAIN_SWAPCHAIN_LOCAL_EXT;
            default -> new VkTimeDomainKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
