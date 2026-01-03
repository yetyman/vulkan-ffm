package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkDependencyFlagBits
 * Generated from jextract bindings
 */
public record VkDependencyFlagBits(int value) {

    public static final VkDependencyFlagBits VK_DEPENDENCY_ASYMMETRIC_EVENT_BIT_KHR = new VkDependencyFlagBits(64);
    public static final VkDependencyFlagBits VK_DEPENDENCY_BY_REGION_BIT = new VkDependencyFlagBits(1);
    public static final VkDependencyFlagBits VK_DEPENDENCY_DEVICE_GROUP_BIT = new VkDependencyFlagBits(4);
    public static final VkDependencyFlagBits VK_DEPENDENCY_DEVICE_GROUP_BIT_KHR = new VkDependencyFlagBits(4);
    public static final VkDependencyFlagBits VK_DEPENDENCY_FEEDBACK_LOOP_BIT_EXT = new VkDependencyFlagBits(8);
    public static final VkDependencyFlagBits VK_DEPENDENCY_FLAG_BITS_MAX_ENUM = new VkDependencyFlagBits(2147483647);
    public static final VkDependencyFlagBits VK_DEPENDENCY_QUEUE_FAMILY_OWNERSHIP_TRANSFER_USE_ALL_STAGES_BIT_KHR = new VkDependencyFlagBits(32);
    public static final VkDependencyFlagBits VK_DEPENDENCY_VIEW_LOCAL_BIT = new VkDependencyFlagBits(2);
    public static final VkDependencyFlagBits VK_DEPENDENCY_VIEW_LOCAL_BIT_KHR = new VkDependencyFlagBits(2);

    public static VkDependencyFlagBits fromValue(int value) {
        return switch (value) {
            case 64 -> VK_DEPENDENCY_ASYMMETRIC_EVENT_BIT_KHR;
            case 1 -> VK_DEPENDENCY_BY_REGION_BIT;
            case 4 -> VK_DEPENDENCY_DEVICE_GROUP_BIT;
            case 8 -> VK_DEPENDENCY_FEEDBACK_LOOP_BIT_EXT;
            case 2147483647 -> VK_DEPENDENCY_FLAG_BITS_MAX_ENUM;
            case 32 -> VK_DEPENDENCY_QUEUE_FAMILY_OWNERSHIP_TRANSFER_USE_ALL_STAGES_BIT_KHR;
            case 2 -> VK_DEPENDENCY_VIEW_LOCAL_BIT;
            default -> new VkDependencyFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
