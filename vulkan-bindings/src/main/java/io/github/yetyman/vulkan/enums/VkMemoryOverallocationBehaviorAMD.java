package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkMemoryOverallocationBehaviorAMD
 * Generated from jextract bindings
 */
public record VkMemoryOverallocationBehaviorAMD(int value) {

    public static final VkMemoryOverallocationBehaviorAMD VK_MEMORY_OVERALLOCATION_BEHAVIOR_ALLOWED_AMD = new VkMemoryOverallocationBehaviorAMD(1);
    public static final VkMemoryOverallocationBehaviorAMD VK_MEMORY_OVERALLOCATION_BEHAVIOR_DEFAULT_AMD = new VkMemoryOverallocationBehaviorAMD(0);
    public static final VkMemoryOverallocationBehaviorAMD VK_MEMORY_OVERALLOCATION_BEHAVIOR_DISALLOWED_AMD = new VkMemoryOverallocationBehaviorAMD(2);
    public static final VkMemoryOverallocationBehaviorAMD VK_MEMORY_OVERALLOCATION_BEHAVIOR_MAX_ENUM_AMD = new VkMemoryOverallocationBehaviorAMD(2147483647);

    public static VkMemoryOverallocationBehaviorAMD fromValue(int value) {
        return switch (value) {
            case 1 -> VK_MEMORY_OVERALLOCATION_BEHAVIOR_ALLOWED_AMD;
            case 0 -> VK_MEMORY_OVERALLOCATION_BEHAVIOR_DEFAULT_AMD;
            case 2 -> VK_MEMORY_OVERALLOCATION_BEHAVIOR_DISALLOWED_AMD;
            case 2147483647 -> VK_MEMORY_OVERALLOCATION_BEHAVIOR_MAX_ENUM_AMD;
            default -> new VkMemoryOverallocationBehaviorAMD(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
