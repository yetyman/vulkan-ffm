package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkSamplerCreateFlagBits
 * Generated from jextract bindings
 */
public record VkSamplerCreateFlagBits(int value) {

    public static final VkSamplerCreateFlagBits VK_SAMPLER_CREATE_DESCRIPTOR_BUFFER_CAPTURE_REPLAY_BIT_EXT = new VkSamplerCreateFlagBits(8);
    public static final VkSamplerCreateFlagBits VK_SAMPLER_CREATE_FLAG_BITS_MAX_ENUM = new VkSamplerCreateFlagBits(2147483647);
    public static final VkSamplerCreateFlagBits VK_SAMPLER_CREATE_IMAGE_PROCESSING_BIT_QCOM = new VkSamplerCreateFlagBits(16);
    public static final VkSamplerCreateFlagBits VK_SAMPLER_CREATE_NON_SEAMLESS_CUBE_MAP_BIT_EXT = new VkSamplerCreateFlagBits(4);
    public static final VkSamplerCreateFlagBits VK_SAMPLER_CREATE_SUBSAMPLED_BIT_EXT = new VkSamplerCreateFlagBits(1);
    public static final VkSamplerCreateFlagBits VK_SAMPLER_CREATE_SUBSAMPLED_COARSE_RECONSTRUCTION_BIT_EXT = new VkSamplerCreateFlagBits(2);

    public static VkSamplerCreateFlagBits fromValue(int value) {
        return switch (value) {
            case 8 -> VK_SAMPLER_CREATE_DESCRIPTOR_BUFFER_CAPTURE_REPLAY_BIT_EXT;
            case 2147483647 -> VK_SAMPLER_CREATE_FLAG_BITS_MAX_ENUM;
            case 16 -> VK_SAMPLER_CREATE_IMAGE_PROCESSING_BIT_QCOM;
            case 4 -> VK_SAMPLER_CREATE_NON_SEAMLESS_CUBE_MAP_BIT_EXT;
            case 1 -> VK_SAMPLER_CREATE_SUBSAMPLED_BIT_EXT;
            case 2 -> VK_SAMPLER_CREATE_SUBSAMPLED_COARSE_RECONSTRUCTION_BIT_EXT;
            default -> new VkSamplerCreateFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
