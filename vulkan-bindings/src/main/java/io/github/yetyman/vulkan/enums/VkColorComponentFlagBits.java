package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkColorComponentFlagBits
 * Generated from jextract bindings
 */
public record VkColorComponentFlagBits(int value) {

    public static final VkColorComponentFlagBits VK_COLOR_COMPONENT_A_BIT = new VkColorComponentFlagBits(8);
    public static final VkColorComponentFlagBits VK_COLOR_COMPONENT_B_BIT = new VkColorComponentFlagBits(4);
    public static final VkColorComponentFlagBits VK_COLOR_COMPONENT_FLAG_BITS_MAX_ENUM = new VkColorComponentFlagBits(2147483647);
    public static final VkColorComponentFlagBits VK_COLOR_COMPONENT_G_BIT = new VkColorComponentFlagBits(2);
    public static final VkColorComponentFlagBits VK_COLOR_COMPONENT_R_BIT = new VkColorComponentFlagBits(1);

    public static VkColorComponentFlagBits fromValue(int value) {
        return switch (value) {
            case 8 -> VK_COLOR_COMPONENT_A_BIT;
            case 4 -> VK_COLOR_COMPONENT_B_BIT;
            case 2147483647 -> VK_COLOR_COMPONENT_FLAG_BITS_MAX_ENUM;
            case 2 -> VK_COLOR_COMPONENT_G_BIT;
            case 1 -> VK_COLOR_COMPONENT_R_BIT;
            default -> new VkColorComponentFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
