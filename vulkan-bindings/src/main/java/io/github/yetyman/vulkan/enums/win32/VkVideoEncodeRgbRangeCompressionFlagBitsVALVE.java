package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkVideoEncodeRgbRangeCompressionFlagBitsVALVE
 * Generated from jextract bindings
 */
public record VkVideoEncodeRgbRangeCompressionFlagBitsVALVE(int value) {

    public static final VkVideoEncodeRgbRangeCompressionFlagBitsVALVE VK_VIDEO_ENCODE_RGB_RANGE_COMPRESSION_FLAG_BITS_MAX_ENUM_VALVE = new VkVideoEncodeRgbRangeCompressionFlagBitsVALVE(2147483647);
    public static final VkVideoEncodeRgbRangeCompressionFlagBitsVALVE VK_VIDEO_ENCODE_RGB_RANGE_COMPRESSION_FULL_RANGE_BIT_VALVE = new VkVideoEncodeRgbRangeCompressionFlagBitsVALVE(1);
    public static final VkVideoEncodeRgbRangeCompressionFlagBitsVALVE VK_VIDEO_ENCODE_RGB_RANGE_COMPRESSION_NARROW_RANGE_BIT_VALVE = new VkVideoEncodeRgbRangeCompressionFlagBitsVALVE(2);

    public static VkVideoEncodeRgbRangeCompressionFlagBitsVALVE fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_VIDEO_ENCODE_RGB_RANGE_COMPRESSION_FLAG_BITS_MAX_ENUM_VALVE;
            case 1 -> VK_VIDEO_ENCODE_RGB_RANGE_COMPRESSION_FULL_RANGE_BIT_VALVE;
            case 2 -> VK_VIDEO_ENCODE_RGB_RANGE_COMPRESSION_NARROW_RANGE_BIT_VALVE;
            default -> new VkVideoEncodeRgbRangeCompressionFlagBitsVALVE(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
