package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkCullModeFlagBits
 * Generated from jextract bindings
 */
public record VkCullModeFlagBits(int value) {

    public static final VkCullModeFlagBits VK_CULL_MODE_BACK_BIT = new VkCullModeFlagBits(2);
    public static final VkCullModeFlagBits VK_CULL_MODE_FLAG_BITS_MAX_ENUM = new VkCullModeFlagBits(2147483647);
    public static final VkCullModeFlagBits VK_CULL_MODE_FRONT_AND_BACK = new VkCullModeFlagBits(3);
    public static final VkCullModeFlagBits VK_CULL_MODE_FRONT_BIT = new VkCullModeFlagBits(1);
    public static final VkCullModeFlagBits VK_CULL_MODE_NONE = new VkCullModeFlagBits(0);

    public static VkCullModeFlagBits fromValue(int value) {
        return switch (value) {
            case 2 -> VK_CULL_MODE_BACK_BIT;
            case 2147483647 -> VK_CULL_MODE_FLAG_BITS_MAX_ENUM;
            case 3 -> VK_CULL_MODE_FRONT_AND_BACK;
            case 1 -> VK_CULL_MODE_FRONT_BIT;
            case 0 -> VK_CULL_MODE_NONE;
            default -> new VkCullModeFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
