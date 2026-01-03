package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkPolygonMode
 * Generated from jextract bindings
 */
public record VkPolygonMode(int value) {

    public static final VkPolygonMode VK_POLYGON_MODE_FILL = new VkPolygonMode(0);
    public static final VkPolygonMode VK_POLYGON_MODE_FILL_RECTANGLE_NV = new VkPolygonMode(1000153000);
    public static final VkPolygonMode VK_POLYGON_MODE_LINE = new VkPolygonMode(1);
    public static final VkPolygonMode VK_POLYGON_MODE_MAX_ENUM = new VkPolygonMode(2147483647);
    public static final VkPolygonMode VK_POLYGON_MODE_POINT = new VkPolygonMode(2);

    public static VkPolygonMode fromValue(int value) {
        return switch (value) {
            case 0 -> VK_POLYGON_MODE_FILL;
            case 1000153000 -> VK_POLYGON_MODE_FILL_RECTANGLE_NV;
            case 1 -> VK_POLYGON_MODE_LINE;
            case 2147483647 -> VK_POLYGON_MODE_MAX_ENUM;
            case 2 -> VK_POLYGON_MODE_POINT;
            default -> new VkPolygonMode(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
