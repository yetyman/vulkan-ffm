package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkLineRasterizationMode
 * Generated from jextract bindings
 */
public record VkLineRasterizationMode(int value) {

    public static final VkLineRasterizationMode VK_LINE_RASTERIZATION_MODE_BRESENHAM = new VkLineRasterizationMode(2);
    public static final VkLineRasterizationMode VK_LINE_RASTERIZATION_MODE_BRESENHAM_EXT = new VkLineRasterizationMode(2);
    public static final VkLineRasterizationMode VK_LINE_RASTERIZATION_MODE_BRESENHAM_KHR = new VkLineRasterizationMode(2);
    public static final VkLineRasterizationMode VK_LINE_RASTERIZATION_MODE_DEFAULT = new VkLineRasterizationMode(0);
    public static final VkLineRasterizationMode VK_LINE_RASTERIZATION_MODE_DEFAULT_EXT = new VkLineRasterizationMode(0);
    public static final VkLineRasterizationMode VK_LINE_RASTERIZATION_MODE_DEFAULT_KHR = new VkLineRasterizationMode(0);
    public static final VkLineRasterizationMode VK_LINE_RASTERIZATION_MODE_MAX_ENUM = new VkLineRasterizationMode(2147483647);
    public static final VkLineRasterizationMode VK_LINE_RASTERIZATION_MODE_RECTANGULAR = new VkLineRasterizationMode(1);
    public static final VkLineRasterizationMode VK_LINE_RASTERIZATION_MODE_RECTANGULAR_EXT = new VkLineRasterizationMode(1);
    public static final VkLineRasterizationMode VK_LINE_RASTERIZATION_MODE_RECTANGULAR_KHR = new VkLineRasterizationMode(1);
    public static final VkLineRasterizationMode VK_LINE_RASTERIZATION_MODE_RECTANGULAR_SMOOTH = new VkLineRasterizationMode(3);
    public static final VkLineRasterizationMode VK_LINE_RASTERIZATION_MODE_RECTANGULAR_SMOOTH_EXT = new VkLineRasterizationMode(3);
    public static final VkLineRasterizationMode VK_LINE_RASTERIZATION_MODE_RECTANGULAR_SMOOTH_KHR = new VkLineRasterizationMode(3);

    public static VkLineRasterizationMode fromValue(int value) {
        return switch (value) {
            case 2 -> VK_LINE_RASTERIZATION_MODE_BRESENHAM;
            case 0 -> VK_LINE_RASTERIZATION_MODE_DEFAULT;
            case 2147483647 -> VK_LINE_RASTERIZATION_MODE_MAX_ENUM;
            case 1 -> VK_LINE_RASTERIZATION_MODE_RECTANGULAR;
            case 3 -> VK_LINE_RASTERIZATION_MODE_RECTANGULAR_SMOOTH;
            default -> new VkLineRasterizationMode(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
