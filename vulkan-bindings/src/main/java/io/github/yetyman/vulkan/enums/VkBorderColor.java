package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkBorderColor
 * Generated from jextract bindings
 */
public record VkBorderColor(int value) {

    public static final VkBorderColor VK_BORDER_COLOR_FLOAT_CUSTOM_EXT = new VkBorderColor(1000287003);
    public static final VkBorderColor VK_BORDER_COLOR_FLOAT_OPAQUE_BLACK = new VkBorderColor(2);
    public static final VkBorderColor VK_BORDER_COLOR_FLOAT_OPAQUE_WHITE = new VkBorderColor(4);
    public static final VkBorderColor VK_BORDER_COLOR_FLOAT_TRANSPARENT_BLACK = new VkBorderColor(0);
    public static final VkBorderColor VK_BORDER_COLOR_INT_CUSTOM_EXT = new VkBorderColor(1000287004);
    public static final VkBorderColor VK_BORDER_COLOR_INT_OPAQUE_BLACK = new VkBorderColor(3);
    public static final VkBorderColor VK_BORDER_COLOR_INT_OPAQUE_WHITE = new VkBorderColor(5);
    public static final VkBorderColor VK_BORDER_COLOR_INT_TRANSPARENT_BLACK = new VkBorderColor(1);
    public static final VkBorderColor VK_BORDER_COLOR_MAX_ENUM = new VkBorderColor(2147483647);

    public static VkBorderColor fromValue(int value) {
        return switch (value) {
            case 1000287003 -> VK_BORDER_COLOR_FLOAT_CUSTOM_EXT;
            case 2 -> VK_BORDER_COLOR_FLOAT_OPAQUE_BLACK;
            case 4 -> VK_BORDER_COLOR_FLOAT_OPAQUE_WHITE;
            case 0 -> VK_BORDER_COLOR_FLOAT_TRANSPARENT_BLACK;
            case 1000287004 -> VK_BORDER_COLOR_INT_CUSTOM_EXT;
            case 3 -> VK_BORDER_COLOR_INT_OPAQUE_BLACK;
            case 5 -> VK_BORDER_COLOR_INT_OPAQUE_WHITE;
            case 1 -> VK_BORDER_COLOR_INT_TRANSPARENT_BLACK;
            case 2147483647 -> VK_BORDER_COLOR_MAX_ENUM;
            default -> new VkBorderColor(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
