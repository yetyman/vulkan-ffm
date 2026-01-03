package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkVideoEncodeH264RateControlFlagBitsKHR
 * Generated from jextract bindings
 */
public record VkVideoEncodeH264RateControlFlagBitsKHR(int value) {

    public static final VkVideoEncodeH264RateControlFlagBitsKHR VK_VIDEO_ENCODE_H264_RATE_CONTROL_ATTEMPT_HRD_COMPLIANCE_BIT_KHR = new VkVideoEncodeH264RateControlFlagBitsKHR(1);
    public static final VkVideoEncodeH264RateControlFlagBitsKHR VK_VIDEO_ENCODE_H264_RATE_CONTROL_FLAG_BITS_MAX_ENUM_KHR = new VkVideoEncodeH264RateControlFlagBitsKHR(2147483647);
    public static final VkVideoEncodeH264RateControlFlagBitsKHR VK_VIDEO_ENCODE_H264_RATE_CONTROL_REFERENCE_PATTERN_DYADIC_BIT_KHR = new VkVideoEncodeH264RateControlFlagBitsKHR(8);
    public static final VkVideoEncodeH264RateControlFlagBitsKHR VK_VIDEO_ENCODE_H264_RATE_CONTROL_REFERENCE_PATTERN_FLAT_BIT_KHR = new VkVideoEncodeH264RateControlFlagBitsKHR(4);
    public static final VkVideoEncodeH264RateControlFlagBitsKHR VK_VIDEO_ENCODE_H264_RATE_CONTROL_REGULAR_GOP_BIT_KHR = new VkVideoEncodeH264RateControlFlagBitsKHR(2);
    public static final VkVideoEncodeH264RateControlFlagBitsKHR VK_VIDEO_ENCODE_H264_RATE_CONTROL_TEMPORAL_LAYER_PATTERN_DYADIC_BIT_KHR = new VkVideoEncodeH264RateControlFlagBitsKHR(16);

    public static VkVideoEncodeH264RateControlFlagBitsKHR fromValue(int value) {
        return switch (value) {
            case 1 -> VK_VIDEO_ENCODE_H264_RATE_CONTROL_ATTEMPT_HRD_COMPLIANCE_BIT_KHR;
            case 2147483647 -> VK_VIDEO_ENCODE_H264_RATE_CONTROL_FLAG_BITS_MAX_ENUM_KHR;
            case 8 -> VK_VIDEO_ENCODE_H264_RATE_CONTROL_REFERENCE_PATTERN_DYADIC_BIT_KHR;
            case 4 -> VK_VIDEO_ENCODE_H264_RATE_CONTROL_REFERENCE_PATTERN_FLAT_BIT_KHR;
            case 2 -> VK_VIDEO_ENCODE_H264_RATE_CONTROL_REGULAR_GOP_BIT_KHR;
            case 16 -> VK_VIDEO_ENCODE_H264_RATE_CONTROL_TEMPORAL_LAYER_PATTERN_DYADIC_BIT_KHR;
            default -> new VkVideoEncodeH264RateControlFlagBitsKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
