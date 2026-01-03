package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkDisplayPlaneAlphaFlagBitsKHR
 * Generated from jextract bindings
 */
public record VkDisplayPlaneAlphaFlagBitsKHR(int value) {

    public static final VkDisplayPlaneAlphaFlagBitsKHR VK_DISPLAY_PLANE_ALPHA_FLAG_BITS_MAX_ENUM_KHR = new VkDisplayPlaneAlphaFlagBitsKHR(2147483647);
    public static final VkDisplayPlaneAlphaFlagBitsKHR VK_DISPLAY_PLANE_ALPHA_GLOBAL_BIT_KHR = new VkDisplayPlaneAlphaFlagBitsKHR(2);
    public static final VkDisplayPlaneAlphaFlagBitsKHR VK_DISPLAY_PLANE_ALPHA_OPAQUE_BIT_KHR = new VkDisplayPlaneAlphaFlagBitsKHR(1);
    public static final VkDisplayPlaneAlphaFlagBitsKHR VK_DISPLAY_PLANE_ALPHA_PER_PIXEL_BIT_KHR = new VkDisplayPlaneAlphaFlagBitsKHR(4);
    public static final VkDisplayPlaneAlphaFlagBitsKHR VK_DISPLAY_PLANE_ALPHA_PER_PIXEL_PREMULTIPLIED_BIT_KHR = new VkDisplayPlaneAlphaFlagBitsKHR(8);

    public static VkDisplayPlaneAlphaFlagBitsKHR fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_DISPLAY_PLANE_ALPHA_FLAG_BITS_MAX_ENUM_KHR;
            case 2 -> VK_DISPLAY_PLANE_ALPHA_GLOBAL_BIT_KHR;
            case 1 -> VK_DISPLAY_PLANE_ALPHA_OPAQUE_BIT_KHR;
            case 4 -> VK_DISPLAY_PLANE_ALPHA_PER_PIXEL_BIT_KHR;
            case 8 -> VK_DISPLAY_PLANE_ALPHA_PER_PIXEL_PREMULTIPLIED_BIT_KHR;
            default -> new VkDisplayPlaneAlphaFlagBitsKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
