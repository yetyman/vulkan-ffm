package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkInternalAllocationType
 * Generated from jextract bindings
 */
public record VkInternalAllocationType(int value) {

    public static final VkInternalAllocationType VK_INTERNAL_ALLOCATION_TYPE_EXECUTABLE = new VkInternalAllocationType(0);
    public static final VkInternalAllocationType VK_INTERNAL_ALLOCATION_TYPE_MAX_ENUM = new VkInternalAllocationType(2147483647);

    public static VkInternalAllocationType fromValue(int value) {
        return switch (value) {
            case 0 -> VK_INTERNAL_ALLOCATION_TYPE_EXECUTABLE;
            case 2147483647 -> VK_INTERNAL_ALLOCATION_TYPE_MAX_ENUM;
            default -> new VkInternalAllocationType(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
