package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkBlendOverlapEXT
 * Generated from jextract bindings
 */
public record VkBlendOverlapEXT(int value) {

    public static final VkBlendOverlapEXT VK_BLEND_OVERLAP_CONJOINT_EXT = new VkBlendOverlapEXT(2);
    public static final VkBlendOverlapEXT VK_BLEND_OVERLAP_DISJOINT_EXT = new VkBlendOverlapEXT(1);
    public static final VkBlendOverlapEXT VK_BLEND_OVERLAP_MAX_ENUM_EXT = new VkBlendOverlapEXT(2147483647);
    public static final VkBlendOverlapEXT VK_BLEND_OVERLAP_UNCORRELATED_EXT = new VkBlendOverlapEXT(0);

    public static VkBlendOverlapEXT fromValue(int value) {
        return switch (value) {
            case 2 -> VK_BLEND_OVERLAP_CONJOINT_EXT;
            case 1 -> VK_BLEND_OVERLAP_DISJOINT_EXT;
            case 2147483647 -> VK_BLEND_OVERLAP_MAX_ENUM_EXT;
            case 0 -> VK_BLEND_OVERLAP_UNCORRELATED_EXT;
            default -> new VkBlendOverlapEXT(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
