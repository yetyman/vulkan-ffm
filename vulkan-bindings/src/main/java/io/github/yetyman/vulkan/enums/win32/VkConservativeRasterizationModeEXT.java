package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkConservativeRasterizationModeEXT
 * Generated from jextract bindings
 */
public record VkConservativeRasterizationModeEXT(int value) {

    public static final VkConservativeRasterizationModeEXT VK_CONSERVATIVE_RASTERIZATION_MODE_DISABLED_EXT = new VkConservativeRasterizationModeEXT(0);
    public static final VkConservativeRasterizationModeEXT VK_CONSERVATIVE_RASTERIZATION_MODE_MAX_ENUM_EXT = new VkConservativeRasterizationModeEXT(2147483647);
    public static final VkConservativeRasterizationModeEXT VK_CONSERVATIVE_RASTERIZATION_MODE_OVERESTIMATE_EXT = new VkConservativeRasterizationModeEXT(1);
    public static final VkConservativeRasterizationModeEXT VK_CONSERVATIVE_RASTERIZATION_MODE_UNDERESTIMATE_EXT = new VkConservativeRasterizationModeEXT(2);

    public static VkConservativeRasterizationModeEXT fromValue(int value) {
        return switch (value) {
            case 0 -> VK_CONSERVATIVE_RASTERIZATION_MODE_DISABLED_EXT;
            case 2147483647 -> VK_CONSERVATIVE_RASTERIZATION_MODE_MAX_ENUM_EXT;
            case 1 -> VK_CONSERVATIVE_RASTERIZATION_MODE_OVERESTIMATE_EXT;
            case 2 -> VK_CONSERVATIVE_RASTERIZATION_MODE_UNDERESTIMATE_EXT;
            default -> new VkConservativeRasterizationModeEXT(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
