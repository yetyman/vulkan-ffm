package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkCompositeAlphaFlagBitsKHR
 * Generated from jextract bindings
 */
public record VkCompositeAlphaFlagBitsKHR(int value) {

    public static final VkCompositeAlphaFlagBitsKHR VK_COMPOSITE_ALPHA_FLAG_BITS_MAX_ENUM_KHR = new VkCompositeAlphaFlagBitsKHR(2147483647);
    public static final VkCompositeAlphaFlagBitsKHR VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR = new VkCompositeAlphaFlagBitsKHR(8);
    public static final VkCompositeAlphaFlagBitsKHR VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR = new VkCompositeAlphaFlagBitsKHR(1);
    public static final VkCompositeAlphaFlagBitsKHR VK_COMPOSITE_ALPHA_POST_MULTIPLIED_BIT_KHR = new VkCompositeAlphaFlagBitsKHR(4);
    public static final VkCompositeAlphaFlagBitsKHR VK_COMPOSITE_ALPHA_PRE_MULTIPLIED_BIT_KHR = new VkCompositeAlphaFlagBitsKHR(2);

    public static VkCompositeAlphaFlagBitsKHR fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_COMPOSITE_ALPHA_FLAG_BITS_MAX_ENUM_KHR;
            case 8 -> VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR;
            case 1 -> VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
            case 4 -> VK_COMPOSITE_ALPHA_POST_MULTIPLIED_BIT_KHR;
            case 2 -> VK_COMPOSITE_ALPHA_PRE_MULTIPLIED_BIT_KHR;
            default -> new VkCompositeAlphaFlagBitsKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
