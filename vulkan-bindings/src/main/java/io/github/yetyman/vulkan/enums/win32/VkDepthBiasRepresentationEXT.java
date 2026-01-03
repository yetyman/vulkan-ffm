package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkDepthBiasRepresentationEXT
 * Generated from jextract bindings
 */
public record VkDepthBiasRepresentationEXT(int value) {

    public static final VkDepthBiasRepresentationEXT VK_DEPTH_BIAS_REPRESENTATION_FLOAT_EXT = new VkDepthBiasRepresentationEXT(2);
    public static final VkDepthBiasRepresentationEXT VK_DEPTH_BIAS_REPRESENTATION_LEAST_REPRESENTABLE_VALUE_FORCE_UNORM_EXT = new VkDepthBiasRepresentationEXT(1);
    public static final VkDepthBiasRepresentationEXT VK_DEPTH_BIAS_REPRESENTATION_LEAST_REPRESENTABLE_VALUE_FORMAT_EXT = new VkDepthBiasRepresentationEXT(0);
    public static final VkDepthBiasRepresentationEXT VK_DEPTH_BIAS_REPRESENTATION_MAX_ENUM_EXT = new VkDepthBiasRepresentationEXT(2147483647);

    public static VkDepthBiasRepresentationEXT fromValue(int value) {
        return switch (value) {
            case 2 -> VK_DEPTH_BIAS_REPRESENTATION_FLOAT_EXT;
            case 1 -> VK_DEPTH_BIAS_REPRESENTATION_LEAST_REPRESENTABLE_VALUE_FORCE_UNORM_EXT;
            case 0 -> VK_DEPTH_BIAS_REPRESENTATION_LEAST_REPRESENTABLE_VALUE_FORMAT_EXT;
            case 2147483647 -> VK_DEPTH_BIAS_REPRESENTATION_MAX_ENUM_EXT;
            default -> new VkDepthBiasRepresentationEXT(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
