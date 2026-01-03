package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkOpacityMicromapSpecialIndexEXT
 * Generated from jextract bindings
 */
public record VkOpacityMicromapSpecialIndexEXT(int value) {

    public static final VkOpacityMicromapSpecialIndexEXT VK_OPACITY_MICROMAP_SPECIAL_INDEX_CLUSTER_GEOMETRY_DISABLE_OPACITY_MICROMAP_NV = new VkOpacityMicromapSpecialIndexEXT(-5);
    public static final VkOpacityMicromapSpecialIndexEXT VK_OPACITY_MICROMAP_SPECIAL_INDEX_FULLY_OPAQUE_EXT = new VkOpacityMicromapSpecialIndexEXT(-2);
    public static final VkOpacityMicromapSpecialIndexEXT VK_OPACITY_MICROMAP_SPECIAL_INDEX_FULLY_TRANSPARENT_EXT = new VkOpacityMicromapSpecialIndexEXT(-1);
    public static final VkOpacityMicromapSpecialIndexEXT VK_OPACITY_MICROMAP_SPECIAL_INDEX_FULLY_UNKNOWN_OPAQUE_EXT = new VkOpacityMicromapSpecialIndexEXT(-4);
    public static final VkOpacityMicromapSpecialIndexEXT VK_OPACITY_MICROMAP_SPECIAL_INDEX_FULLY_UNKNOWN_TRANSPARENT_EXT = new VkOpacityMicromapSpecialIndexEXT(-3);
    public static final VkOpacityMicromapSpecialIndexEXT VK_OPACITY_MICROMAP_SPECIAL_INDEX_MAX_ENUM_EXT = new VkOpacityMicromapSpecialIndexEXT(2147483647);

    public static VkOpacityMicromapSpecialIndexEXT fromValue(int value) {
        return switch (value) {
            case -5 -> VK_OPACITY_MICROMAP_SPECIAL_INDEX_CLUSTER_GEOMETRY_DISABLE_OPACITY_MICROMAP_NV;
            case -2 -> VK_OPACITY_MICROMAP_SPECIAL_INDEX_FULLY_OPAQUE_EXT;
            case -1 -> VK_OPACITY_MICROMAP_SPECIAL_INDEX_FULLY_TRANSPARENT_EXT;
            case -4 -> VK_OPACITY_MICROMAP_SPECIAL_INDEX_FULLY_UNKNOWN_OPAQUE_EXT;
            case -3 -> VK_OPACITY_MICROMAP_SPECIAL_INDEX_FULLY_UNKNOWN_TRANSPARENT_EXT;
            case 2147483647 -> VK_OPACITY_MICROMAP_SPECIAL_INDEX_MAX_ENUM_EXT;
            default -> new VkOpacityMicromapSpecialIndexEXT(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
