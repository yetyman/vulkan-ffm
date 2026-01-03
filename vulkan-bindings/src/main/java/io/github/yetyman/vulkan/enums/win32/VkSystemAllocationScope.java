package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkSystemAllocationScope
 * Generated from jextract bindings
 */
public record VkSystemAllocationScope(int value) {

    public static final VkSystemAllocationScope VK_SYSTEM_ALLOCATION_SCOPE_CACHE = new VkSystemAllocationScope(2);
    public static final VkSystemAllocationScope VK_SYSTEM_ALLOCATION_SCOPE_COMMAND = new VkSystemAllocationScope(0);
    public static final VkSystemAllocationScope VK_SYSTEM_ALLOCATION_SCOPE_DEVICE = new VkSystemAllocationScope(3);
    public static final VkSystemAllocationScope VK_SYSTEM_ALLOCATION_SCOPE_INSTANCE = new VkSystemAllocationScope(4);
    public static final VkSystemAllocationScope VK_SYSTEM_ALLOCATION_SCOPE_MAX_ENUM = new VkSystemAllocationScope(2147483647);
    public static final VkSystemAllocationScope VK_SYSTEM_ALLOCATION_SCOPE_OBJECT = new VkSystemAllocationScope(1);

    public static VkSystemAllocationScope fromValue(int value) {
        return switch (value) {
            case 2 -> VK_SYSTEM_ALLOCATION_SCOPE_CACHE;
            case 0 -> VK_SYSTEM_ALLOCATION_SCOPE_COMMAND;
            case 3 -> VK_SYSTEM_ALLOCATION_SCOPE_DEVICE;
            case 4 -> VK_SYSTEM_ALLOCATION_SCOPE_INSTANCE;
            case 2147483647 -> VK_SYSTEM_ALLOCATION_SCOPE_MAX_ENUM;
            case 1 -> VK_SYSTEM_ALLOCATION_SCOPE_OBJECT;
            default -> new VkSystemAllocationScope(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
