package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkComponentSwizzle
 * Generated from jextract bindings
 */
public record VkComponentSwizzle(int value) {

    public static final VkComponentSwizzle VK_COMPONENT_SWIZZLE_A = new VkComponentSwizzle(6);
    public static final VkComponentSwizzle VK_COMPONENT_SWIZZLE_B = new VkComponentSwizzle(5);
    public static final VkComponentSwizzle VK_COMPONENT_SWIZZLE_G = new VkComponentSwizzle(4);
    public static final VkComponentSwizzle VK_COMPONENT_SWIZZLE_IDENTITY = new VkComponentSwizzle(0);
    public static final VkComponentSwizzle VK_COMPONENT_SWIZZLE_MAX_ENUM = new VkComponentSwizzle(2147483647);
    public static final VkComponentSwizzle VK_COMPONENT_SWIZZLE_ONE = new VkComponentSwizzle(2);
    public static final VkComponentSwizzle VK_COMPONENT_SWIZZLE_R = new VkComponentSwizzle(3);
    public static final VkComponentSwizzle VK_COMPONENT_SWIZZLE_ZERO = new VkComponentSwizzle(1);

    public static VkComponentSwizzle fromValue(int value) {
        return switch (value) {
            case 6 -> VK_COMPONENT_SWIZZLE_A;
            case 5 -> VK_COMPONENT_SWIZZLE_B;
            case 4 -> VK_COMPONENT_SWIZZLE_G;
            case 0 -> VK_COMPONENT_SWIZZLE_IDENTITY;
            case 2147483647 -> VK_COMPONENT_SWIZZLE_MAX_ENUM;
            case 2 -> VK_COMPONENT_SWIZZLE_ONE;
            case 3 -> VK_COMPONENT_SWIZZLE_R;
            case 1 -> VK_COMPONENT_SWIZZLE_ZERO;
            default -> new VkComponentSwizzle(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
