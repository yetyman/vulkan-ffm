package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkSamplerReductionMode
 * Generated from jextract bindings
 */
public record VkSamplerReductionMode(int value) {

    public static final VkSamplerReductionMode VK_SAMPLER_REDUCTION_MODE_MAX = new VkSamplerReductionMode(2);
    public static final VkSamplerReductionMode VK_SAMPLER_REDUCTION_MODE_MAX_ENUM = new VkSamplerReductionMode(2147483647);
    public static final VkSamplerReductionMode VK_SAMPLER_REDUCTION_MODE_MAX_EXT = new VkSamplerReductionMode(2);
    public static final VkSamplerReductionMode VK_SAMPLER_REDUCTION_MODE_MIN = new VkSamplerReductionMode(1);
    public static final VkSamplerReductionMode VK_SAMPLER_REDUCTION_MODE_MIN_EXT = new VkSamplerReductionMode(1);
    public static final VkSamplerReductionMode VK_SAMPLER_REDUCTION_MODE_WEIGHTED_AVERAGE = new VkSamplerReductionMode(0);
    public static final VkSamplerReductionMode VK_SAMPLER_REDUCTION_MODE_WEIGHTED_AVERAGE_EXT = new VkSamplerReductionMode(0);
    public static final VkSamplerReductionMode VK_SAMPLER_REDUCTION_MODE_WEIGHTED_AVERAGE_RANGECLAMP_QCOM = new VkSamplerReductionMode(1000521000);

    public static VkSamplerReductionMode fromValue(int value) {
        return switch (value) {
            case 2 -> VK_SAMPLER_REDUCTION_MODE_MAX;
            case 2147483647 -> VK_SAMPLER_REDUCTION_MODE_MAX_ENUM;
            case 1 -> VK_SAMPLER_REDUCTION_MODE_MIN;
            case 0 -> VK_SAMPLER_REDUCTION_MODE_WEIGHTED_AVERAGE;
            case 1000521000 -> VK_SAMPLER_REDUCTION_MODE_WEIGHTED_AVERAGE_RANGECLAMP_QCOM;
            default -> new VkSamplerReductionMode(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
