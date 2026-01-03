package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkConditionalRenderingFlagBitsEXT
 * Generated from jextract bindings
 */
public record VkConditionalRenderingFlagBitsEXT(int value) {

    public static final VkConditionalRenderingFlagBitsEXT VK_CONDITIONAL_RENDERING_FLAG_BITS_MAX_ENUM_EXT = new VkConditionalRenderingFlagBitsEXT(2147483647);
    public static final VkConditionalRenderingFlagBitsEXT VK_CONDITIONAL_RENDERING_INVERTED_BIT_EXT = new VkConditionalRenderingFlagBitsEXT(1);

    public static VkConditionalRenderingFlagBitsEXT fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_CONDITIONAL_RENDERING_FLAG_BITS_MAX_ENUM_EXT;
            case 1 -> VK_CONDITIONAL_RENDERING_INVERTED_BIT_EXT;
            default -> new VkConditionalRenderingFlagBitsEXT(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
