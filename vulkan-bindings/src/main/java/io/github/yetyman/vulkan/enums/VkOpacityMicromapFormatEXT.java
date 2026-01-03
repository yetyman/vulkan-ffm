package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkOpacityMicromapFormatEXT
 * Generated from jextract bindings
 */
public record VkOpacityMicromapFormatEXT(int value) {

    public static final VkOpacityMicromapFormatEXT VK_OPACITY_MICROMAP_FORMAT_2_STATE_EXT = new VkOpacityMicromapFormatEXT(1);
    public static final VkOpacityMicromapFormatEXT VK_OPACITY_MICROMAP_FORMAT_4_STATE_EXT = new VkOpacityMicromapFormatEXT(2);
    public static final VkOpacityMicromapFormatEXT VK_OPACITY_MICROMAP_FORMAT_MAX_ENUM_EXT = new VkOpacityMicromapFormatEXT(2147483647);

    public static VkOpacityMicromapFormatEXT fromValue(int value) {
        return switch (value) {
            case 1 -> VK_OPACITY_MICROMAP_FORMAT_2_STATE_EXT;
            case 2 -> VK_OPACITY_MICROMAP_FORMAT_4_STATE_EXT;
            case 2147483647 -> VK_OPACITY_MICROMAP_FORMAT_MAX_ENUM_EXT;
            default -> new VkOpacityMicromapFormatEXT(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
