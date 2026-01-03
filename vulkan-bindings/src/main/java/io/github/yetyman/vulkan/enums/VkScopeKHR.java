package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkScopeKHR
 * Generated from jextract bindings
 */
public record VkScopeKHR(int value) {

    public static final VkScopeKHR VK_SCOPE_DEVICE_KHR = new VkScopeKHR(1);
    public static final VkScopeKHR VK_SCOPE_DEVICE_NV = VK_SCOPE_DEVICE_KHR;
    public static final VkScopeKHR VK_SCOPE_MAX_ENUM_KHR = new VkScopeKHR(2147483647);
    public static final VkScopeKHR VK_SCOPE_QUEUE_FAMILY_KHR = new VkScopeKHR(5);
    public static final VkScopeKHR VK_SCOPE_QUEUE_FAMILY_NV = VK_SCOPE_QUEUE_FAMILY_KHR;
    public static final VkScopeKHR VK_SCOPE_SUBGROUP_KHR = new VkScopeKHR(3);
    public static final VkScopeKHR VK_SCOPE_SUBGROUP_NV = VK_SCOPE_SUBGROUP_KHR;
    public static final VkScopeKHR VK_SCOPE_WORKGROUP_KHR = new VkScopeKHR(2);
    public static final VkScopeKHR VK_SCOPE_WORKGROUP_NV = VK_SCOPE_WORKGROUP_KHR;

    public static VkScopeKHR fromValue(int value) {
        return switch (value) {
            case 1 -> VK_SCOPE_DEVICE_NV;
            case 2147483647 -> VK_SCOPE_MAX_ENUM_KHR;
            case 5 -> VK_SCOPE_QUEUE_FAMILY_NV;
            case 3 -> VK_SCOPE_SUBGROUP_NV;
            case 2 -> VK_SCOPE_WORKGROUP_NV;
            default -> new VkScopeKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
