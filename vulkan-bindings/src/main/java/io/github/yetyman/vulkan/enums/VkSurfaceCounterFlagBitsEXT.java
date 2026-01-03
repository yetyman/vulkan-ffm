package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkSurfaceCounterFlagBitsEXT
 * Generated from jextract bindings
 */
public record VkSurfaceCounterFlagBitsEXT(int value) {

    public static final VkSurfaceCounterFlagBitsEXT VK_SURFACE_COUNTER_FLAG_BITS_MAX_ENUM_EXT = new VkSurfaceCounterFlagBitsEXT(2147483647);
    public static final VkSurfaceCounterFlagBitsEXT VK_SURFACE_COUNTER_VBLANK_BIT_EXT = new VkSurfaceCounterFlagBitsEXT(1);
    public static final VkSurfaceCounterFlagBitsEXT VK_SURFACE_COUNTER_VBLANK_EXT = VK_SURFACE_COUNTER_VBLANK_BIT_EXT;

    public static VkSurfaceCounterFlagBitsEXT fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_SURFACE_COUNTER_FLAG_BITS_MAX_ENUM_EXT;
            case 1 -> VK_SURFACE_COUNTER_VBLANK_EXT;
            default -> new VkSurfaceCounterFlagBitsEXT(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
