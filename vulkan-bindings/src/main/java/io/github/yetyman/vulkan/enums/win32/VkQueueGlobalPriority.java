package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkQueueGlobalPriority
 * Generated from jextract bindings
 */
public record VkQueueGlobalPriority(int value) {

    public static final VkQueueGlobalPriority VK_QUEUE_GLOBAL_PRIORITY_HIGH = new VkQueueGlobalPriority(512);
    public static final VkQueueGlobalPriority VK_QUEUE_GLOBAL_PRIORITY_HIGH_EXT = new VkQueueGlobalPriority(512);
    public static final VkQueueGlobalPriority VK_QUEUE_GLOBAL_PRIORITY_HIGH_KHR = new VkQueueGlobalPriority(512);
    public static final VkQueueGlobalPriority VK_QUEUE_GLOBAL_PRIORITY_LOW = new VkQueueGlobalPriority(128);
    public static final VkQueueGlobalPriority VK_QUEUE_GLOBAL_PRIORITY_LOW_EXT = new VkQueueGlobalPriority(128);
    public static final VkQueueGlobalPriority VK_QUEUE_GLOBAL_PRIORITY_LOW_KHR = new VkQueueGlobalPriority(128);
    public static final VkQueueGlobalPriority VK_QUEUE_GLOBAL_PRIORITY_MAX_ENUM = new VkQueueGlobalPriority(2147483647);
    public static final VkQueueGlobalPriority VK_QUEUE_GLOBAL_PRIORITY_MEDIUM = new VkQueueGlobalPriority(256);
    public static final VkQueueGlobalPriority VK_QUEUE_GLOBAL_PRIORITY_MEDIUM_EXT = new VkQueueGlobalPriority(256);
    public static final VkQueueGlobalPriority VK_QUEUE_GLOBAL_PRIORITY_MEDIUM_KHR = new VkQueueGlobalPriority(256);
    public static final VkQueueGlobalPriority VK_QUEUE_GLOBAL_PRIORITY_REALTIME = new VkQueueGlobalPriority(1024);
    public static final VkQueueGlobalPriority VK_QUEUE_GLOBAL_PRIORITY_REALTIME_EXT = new VkQueueGlobalPriority(1024);
    public static final VkQueueGlobalPriority VK_QUEUE_GLOBAL_PRIORITY_REALTIME_KHR = new VkQueueGlobalPriority(1024);

    public static VkQueueGlobalPriority fromValue(int value) {
        return switch (value) {
            case 512 -> VK_QUEUE_GLOBAL_PRIORITY_HIGH;
            case 128 -> VK_QUEUE_GLOBAL_PRIORITY_LOW;
            case 2147483647 -> VK_QUEUE_GLOBAL_PRIORITY_MAX_ENUM;
            case 256 -> VK_QUEUE_GLOBAL_PRIORITY_MEDIUM;
            case 1024 -> VK_QUEUE_GLOBAL_PRIORITY_REALTIME;
            default -> new VkQueueGlobalPriority(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
