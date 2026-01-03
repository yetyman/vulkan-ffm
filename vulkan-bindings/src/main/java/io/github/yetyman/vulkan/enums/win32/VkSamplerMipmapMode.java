package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkSamplerMipmapMode
 * Generated from jextract bindings
 */
public record VkSamplerMipmapMode(int value) {

    public static final VkSamplerMipmapMode VK_SAMPLER_MIPMAP_MODE_LINEAR = new VkSamplerMipmapMode(1);
    public static final VkSamplerMipmapMode VK_SAMPLER_MIPMAP_MODE_MAX_ENUM = new VkSamplerMipmapMode(2147483647);
    public static final VkSamplerMipmapMode VK_SAMPLER_MIPMAP_MODE_NEAREST = new VkSamplerMipmapMode(0);

    public static VkSamplerMipmapMode fromValue(int value) {
        return switch (value) {
            case 1 -> VK_SAMPLER_MIPMAP_MODE_LINEAR;
            case 2147483647 -> VK_SAMPLER_MIPMAP_MODE_MAX_ENUM;
            case 0 -> VK_SAMPLER_MIPMAP_MODE_NEAREST;
            default -> new VkSamplerMipmapMode(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
