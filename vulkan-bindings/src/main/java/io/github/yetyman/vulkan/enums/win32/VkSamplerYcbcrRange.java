package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkSamplerYcbcrRange
 * Generated from jextract bindings
 */
public record VkSamplerYcbcrRange(int value) {

    public static final VkSamplerYcbcrRange VK_SAMPLER_YCBCR_RANGE_ITU_FULL = new VkSamplerYcbcrRange(0);
    public static final VkSamplerYcbcrRange VK_SAMPLER_YCBCR_RANGE_ITU_FULL_KHR = new VkSamplerYcbcrRange(0);
    public static final VkSamplerYcbcrRange VK_SAMPLER_YCBCR_RANGE_ITU_NARROW = new VkSamplerYcbcrRange(1);
    public static final VkSamplerYcbcrRange VK_SAMPLER_YCBCR_RANGE_ITU_NARROW_KHR = new VkSamplerYcbcrRange(1);
    public static final VkSamplerYcbcrRange VK_SAMPLER_YCBCR_RANGE_MAX_ENUM = new VkSamplerYcbcrRange(2147483647);

    public static VkSamplerYcbcrRange fromValue(int value) {
        return switch (value) {
            case 0 -> VK_SAMPLER_YCBCR_RANGE_ITU_FULL;
            case 1 -> VK_SAMPLER_YCBCR_RANGE_ITU_NARROW;
            case 2147483647 -> VK_SAMPLER_YCBCR_RANGE_MAX_ENUM;
            default -> new VkSamplerYcbcrRange(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
