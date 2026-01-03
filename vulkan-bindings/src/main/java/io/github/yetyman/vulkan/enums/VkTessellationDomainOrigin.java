package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkTessellationDomainOrigin
 * Generated from jextract bindings
 */
public record VkTessellationDomainOrigin(int value) {

    public static final VkTessellationDomainOrigin VK_TESSELLATION_DOMAIN_ORIGIN_LOWER_LEFT = new VkTessellationDomainOrigin(1);
    public static final VkTessellationDomainOrigin VK_TESSELLATION_DOMAIN_ORIGIN_LOWER_LEFT_KHR = VK_TESSELLATION_DOMAIN_ORIGIN_LOWER_LEFT;
    public static final VkTessellationDomainOrigin VK_TESSELLATION_DOMAIN_ORIGIN_MAX_ENUM = new VkTessellationDomainOrigin(2147483647);
    public static final VkTessellationDomainOrigin VK_TESSELLATION_DOMAIN_ORIGIN_UPPER_LEFT = new VkTessellationDomainOrigin(0);
    public static final VkTessellationDomainOrigin VK_TESSELLATION_DOMAIN_ORIGIN_UPPER_LEFT_KHR = VK_TESSELLATION_DOMAIN_ORIGIN_UPPER_LEFT;

    public static VkTessellationDomainOrigin fromValue(int value) {
        return switch (value) {
            case 1 -> VK_TESSELLATION_DOMAIN_ORIGIN_LOWER_LEFT;
            case 2147483647 -> VK_TESSELLATION_DOMAIN_ORIGIN_MAX_ENUM;
            case 0 -> VK_TESSELLATION_DOMAIN_ORIGIN_UPPER_LEFT;
            default -> new VkTessellationDomainOrigin(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
