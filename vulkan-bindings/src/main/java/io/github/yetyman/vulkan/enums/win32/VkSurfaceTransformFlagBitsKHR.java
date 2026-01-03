package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkSurfaceTransformFlagBitsKHR
 * Generated from jextract bindings
 */
public record VkSurfaceTransformFlagBitsKHR(int value) {

    public static final VkSurfaceTransformFlagBitsKHR VK_SURFACE_TRANSFORM_FLAG_BITS_MAX_ENUM_KHR = new VkSurfaceTransformFlagBitsKHR(2147483647);
    public static final VkSurfaceTransformFlagBitsKHR VK_SURFACE_TRANSFORM_HORIZONTAL_MIRROR_BIT_KHR = new VkSurfaceTransformFlagBitsKHR(16);
    public static final VkSurfaceTransformFlagBitsKHR VK_SURFACE_TRANSFORM_HORIZONTAL_MIRROR_ROTATE_180_BIT_KHR = new VkSurfaceTransformFlagBitsKHR(64);
    public static final VkSurfaceTransformFlagBitsKHR VK_SURFACE_TRANSFORM_HORIZONTAL_MIRROR_ROTATE_270_BIT_KHR = new VkSurfaceTransformFlagBitsKHR(128);
    public static final VkSurfaceTransformFlagBitsKHR VK_SURFACE_TRANSFORM_HORIZONTAL_MIRROR_ROTATE_90_BIT_KHR = new VkSurfaceTransformFlagBitsKHR(32);
    public static final VkSurfaceTransformFlagBitsKHR VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR = new VkSurfaceTransformFlagBitsKHR(1);
    public static final VkSurfaceTransformFlagBitsKHR VK_SURFACE_TRANSFORM_INHERIT_BIT_KHR = new VkSurfaceTransformFlagBitsKHR(256);
    public static final VkSurfaceTransformFlagBitsKHR VK_SURFACE_TRANSFORM_ROTATE_180_BIT_KHR = new VkSurfaceTransformFlagBitsKHR(4);
    public static final VkSurfaceTransformFlagBitsKHR VK_SURFACE_TRANSFORM_ROTATE_270_BIT_KHR = new VkSurfaceTransformFlagBitsKHR(8);
    public static final VkSurfaceTransformFlagBitsKHR VK_SURFACE_TRANSFORM_ROTATE_90_BIT_KHR = new VkSurfaceTransformFlagBitsKHR(2);

    public static VkSurfaceTransformFlagBitsKHR fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_SURFACE_TRANSFORM_FLAG_BITS_MAX_ENUM_KHR;
            case 16 -> VK_SURFACE_TRANSFORM_HORIZONTAL_MIRROR_BIT_KHR;
            case 64 -> VK_SURFACE_TRANSFORM_HORIZONTAL_MIRROR_ROTATE_180_BIT_KHR;
            case 128 -> VK_SURFACE_TRANSFORM_HORIZONTAL_MIRROR_ROTATE_270_BIT_KHR;
            case 32 -> VK_SURFACE_TRANSFORM_HORIZONTAL_MIRROR_ROTATE_90_BIT_KHR;
            case 1 -> VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR;
            case 256 -> VK_SURFACE_TRANSFORM_INHERIT_BIT_KHR;
            case 4 -> VK_SURFACE_TRANSFORM_ROTATE_180_BIT_KHR;
            case 8 -> VK_SURFACE_TRANSFORM_ROTATE_270_BIT_KHR;
            case 2 -> VK_SURFACE_TRANSFORM_ROTATE_90_BIT_KHR;
            default -> new VkSurfaceTransformFlagBitsKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
