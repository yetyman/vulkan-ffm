package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkVideoEncodeRgbModelConversionFlagBitsVALVE
 * Generated from jextract bindings
 */
public record VkVideoEncodeRgbModelConversionFlagBitsVALVE(int value) {

    public static final VkVideoEncodeRgbModelConversionFlagBitsVALVE VK_VIDEO_ENCODE_RGB_MODEL_CONVERSION_FLAG_BITS_MAX_ENUM_VALVE = new VkVideoEncodeRgbModelConversionFlagBitsVALVE(2147483647);
    public static final VkVideoEncodeRgbModelConversionFlagBitsVALVE VK_VIDEO_ENCODE_RGB_MODEL_CONVERSION_RGB_IDENTITY_BIT_VALVE = new VkVideoEncodeRgbModelConversionFlagBitsVALVE(1);
    public static final VkVideoEncodeRgbModelConversionFlagBitsVALVE VK_VIDEO_ENCODE_RGB_MODEL_CONVERSION_YCBCR_2020_BIT_VALVE = new VkVideoEncodeRgbModelConversionFlagBitsVALVE(16);
    public static final VkVideoEncodeRgbModelConversionFlagBitsVALVE VK_VIDEO_ENCODE_RGB_MODEL_CONVERSION_YCBCR_601_BIT_VALVE = new VkVideoEncodeRgbModelConversionFlagBitsVALVE(8);
    public static final VkVideoEncodeRgbModelConversionFlagBitsVALVE VK_VIDEO_ENCODE_RGB_MODEL_CONVERSION_YCBCR_709_BIT_VALVE = new VkVideoEncodeRgbModelConversionFlagBitsVALVE(4);
    public static final VkVideoEncodeRgbModelConversionFlagBitsVALVE VK_VIDEO_ENCODE_RGB_MODEL_CONVERSION_YCBCR_IDENTITY_BIT_VALVE = new VkVideoEncodeRgbModelConversionFlagBitsVALVE(2);

    public static VkVideoEncodeRgbModelConversionFlagBitsVALVE fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_VIDEO_ENCODE_RGB_MODEL_CONVERSION_FLAG_BITS_MAX_ENUM_VALVE;
            case 1 -> VK_VIDEO_ENCODE_RGB_MODEL_CONVERSION_RGB_IDENTITY_BIT_VALVE;
            case 16 -> VK_VIDEO_ENCODE_RGB_MODEL_CONVERSION_YCBCR_2020_BIT_VALVE;
            case 8 -> VK_VIDEO_ENCODE_RGB_MODEL_CONVERSION_YCBCR_601_BIT_VALVE;
            case 4 -> VK_VIDEO_ENCODE_RGB_MODEL_CONVERSION_YCBCR_709_BIT_VALVE;
            case 2 -> VK_VIDEO_ENCODE_RGB_MODEL_CONVERSION_YCBCR_IDENTITY_BIT_VALVE;
            default -> new VkVideoEncodeRgbModelConversionFlagBitsVALVE(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
