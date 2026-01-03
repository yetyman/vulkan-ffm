package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkViewportCoordinateSwizzleNV
 * Generated from jextract bindings
 */
public record VkViewportCoordinateSwizzleNV(int value) {

    public static final VkViewportCoordinateSwizzleNV VK_VIEWPORT_COORDINATE_SWIZZLE_MAX_ENUM_NV = new VkViewportCoordinateSwizzleNV(2147483647);
    public static final VkViewportCoordinateSwizzleNV VK_VIEWPORT_COORDINATE_SWIZZLE_NEGATIVE_W_NV = new VkViewportCoordinateSwizzleNV(7);
    public static final VkViewportCoordinateSwizzleNV VK_VIEWPORT_COORDINATE_SWIZZLE_NEGATIVE_X_NV = new VkViewportCoordinateSwizzleNV(1);
    public static final VkViewportCoordinateSwizzleNV VK_VIEWPORT_COORDINATE_SWIZZLE_NEGATIVE_Y_NV = new VkViewportCoordinateSwizzleNV(3);
    public static final VkViewportCoordinateSwizzleNV VK_VIEWPORT_COORDINATE_SWIZZLE_NEGATIVE_Z_NV = new VkViewportCoordinateSwizzleNV(5);
    public static final VkViewportCoordinateSwizzleNV VK_VIEWPORT_COORDINATE_SWIZZLE_POSITIVE_W_NV = new VkViewportCoordinateSwizzleNV(6);
    public static final VkViewportCoordinateSwizzleNV VK_VIEWPORT_COORDINATE_SWIZZLE_POSITIVE_X_NV = new VkViewportCoordinateSwizzleNV(0);
    public static final VkViewportCoordinateSwizzleNV VK_VIEWPORT_COORDINATE_SWIZZLE_POSITIVE_Y_NV = new VkViewportCoordinateSwizzleNV(2);
    public static final VkViewportCoordinateSwizzleNV VK_VIEWPORT_COORDINATE_SWIZZLE_POSITIVE_Z_NV = new VkViewportCoordinateSwizzleNV(4);

    public static VkViewportCoordinateSwizzleNV fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_VIEWPORT_COORDINATE_SWIZZLE_MAX_ENUM_NV;
            case 7 -> VK_VIEWPORT_COORDINATE_SWIZZLE_NEGATIVE_W_NV;
            case 1 -> VK_VIEWPORT_COORDINATE_SWIZZLE_NEGATIVE_X_NV;
            case 3 -> VK_VIEWPORT_COORDINATE_SWIZZLE_NEGATIVE_Y_NV;
            case 5 -> VK_VIEWPORT_COORDINATE_SWIZZLE_NEGATIVE_Z_NV;
            case 6 -> VK_VIEWPORT_COORDINATE_SWIZZLE_POSITIVE_W_NV;
            case 0 -> VK_VIEWPORT_COORDINATE_SWIZZLE_POSITIVE_X_NV;
            case 2 -> VK_VIEWPORT_COORDINATE_SWIZZLE_POSITIVE_Y_NV;
            case 4 -> VK_VIEWPORT_COORDINATE_SWIZZLE_POSITIVE_Z_NV;
            default -> new VkViewportCoordinateSwizzleNV(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
