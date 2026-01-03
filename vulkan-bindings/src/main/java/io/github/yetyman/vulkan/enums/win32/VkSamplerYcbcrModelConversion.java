package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkSamplerYcbcrModelConversion
 * Generated from jextract bindings
 */
public record VkSamplerYcbcrModelConversion(int value) {

    public static final VkSamplerYcbcrModelConversion VK_SAMPLER_YCBCR_MODEL_CONVERSION_MAX_ENUM = new VkSamplerYcbcrModelConversion(2147483647);
    public static final VkSamplerYcbcrModelConversion VK_SAMPLER_YCBCR_MODEL_CONVERSION_RGB_IDENTITY = new VkSamplerYcbcrModelConversion(0);
    public static final VkSamplerYcbcrModelConversion VK_SAMPLER_YCBCR_MODEL_CONVERSION_RGB_IDENTITY_KHR = new VkSamplerYcbcrModelConversion(0);
    public static final VkSamplerYcbcrModelConversion VK_SAMPLER_YCBCR_MODEL_CONVERSION_YCBCR_2020 = new VkSamplerYcbcrModelConversion(4);
    public static final VkSamplerYcbcrModelConversion VK_SAMPLER_YCBCR_MODEL_CONVERSION_YCBCR_2020_KHR = new VkSamplerYcbcrModelConversion(4);
    public static final VkSamplerYcbcrModelConversion VK_SAMPLER_YCBCR_MODEL_CONVERSION_YCBCR_601 = new VkSamplerYcbcrModelConversion(3);
    public static final VkSamplerYcbcrModelConversion VK_SAMPLER_YCBCR_MODEL_CONVERSION_YCBCR_601_KHR = new VkSamplerYcbcrModelConversion(3);
    public static final VkSamplerYcbcrModelConversion VK_SAMPLER_YCBCR_MODEL_CONVERSION_YCBCR_709 = new VkSamplerYcbcrModelConversion(2);
    public static final VkSamplerYcbcrModelConversion VK_SAMPLER_YCBCR_MODEL_CONVERSION_YCBCR_709_KHR = new VkSamplerYcbcrModelConversion(2);
    public static final VkSamplerYcbcrModelConversion VK_SAMPLER_YCBCR_MODEL_CONVERSION_YCBCR_IDENTITY = new VkSamplerYcbcrModelConversion(1);
    public static final VkSamplerYcbcrModelConversion VK_SAMPLER_YCBCR_MODEL_CONVERSION_YCBCR_IDENTITY_KHR = new VkSamplerYcbcrModelConversion(1);

    public static VkSamplerYcbcrModelConversion fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_SAMPLER_YCBCR_MODEL_CONVERSION_MAX_ENUM;
            case 0 -> VK_SAMPLER_YCBCR_MODEL_CONVERSION_RGB_IDENTITY;
            case 4 -> VK_SAMPLER_YCBCR_MODEL_CONVERSION_YCBCR_2020;
            case 3 -> VK_SAMPLER_YCBCR_MODEL_CONVERSION_YCBCR_601;
            case 2 -> VK_SAMPLER_YCBCR_MODEL_CONVERSION_YCBCR_709;
            case 1 -> VK_SAMPLER_YCBCR_MODEL_CONVERSION_YCBCR_IDENTITY;
            default -> new VkSamplerYcbcrModelConversion(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
