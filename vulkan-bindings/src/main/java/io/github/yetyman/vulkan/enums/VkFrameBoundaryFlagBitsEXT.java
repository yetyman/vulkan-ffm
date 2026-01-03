package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkFrameBoundaryFlagBitsEXT
 * Generated from jextract bindings
 */
public record VkFrameBoundaryFlagBitsEXT(int value) {

    public static final VkFrameBoundaryFlagBitsEXT VK_FRAME_BOUNDARY_FLAG_BITS_MAX_ENUM_EXT = new VkFrameBoundaryFlagBitsEXT(2147483647);
    public static final VkFrameBoundaryFlagBitsEXT VK_FRAME_BOUNDARY_FRAME_END_BIT_EXT = new VkFrameBoundaryFlagBitsEXT(1);

    public static VkFrameBoundaryFlagBitsEXT fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_FRAME_BOUNDARY_FLAG_BITS_MAX_ENUM_EXT;
            case 1 -> VK_FRAME_BOUNDARY_FRAME_END_BIT_EXT;
            default -> new VkFrameBoundaryFlagBitsEXT(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
