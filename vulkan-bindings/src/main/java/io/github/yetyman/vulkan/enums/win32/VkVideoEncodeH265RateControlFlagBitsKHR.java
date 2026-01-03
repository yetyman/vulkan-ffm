package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkVideoEncodeH265RateControlFlagBitsKHR
 * Generated from jextract bindings
 */
public record VkVideoEncodeH265RateControlFlagBitsKHR(int value) {

    public static final VkVideoEncodeH265RateControlFlagBitsKHR VK_VIDEO_ENCODE_H265_RATE_CONTROL_ATTEMPT_HRD_COMPLIANCE_BIT_KHR = new VkVideoEncodeH265RateControlFlagBitsKHR(1);
    public static final VkVideoEncodeH265RateControlFlagBitsKHR VK_VIDEO_ENCODE_H265_RATE_CONTROL_FLAG_BITS_MAX_ENUM_KHR = new VkVideoEncodeH265RateControlFlagBitsKHR(2147483647);
    public static final VkVideoEncodeH265RateControlFlagBitsKHR VK_VIDEO_ENCODE_H265_RATE_CONTROL_REFERENCE_PATTERN_DYADIC_BIT_KHR = new VkVideoEncodeH265RateControlFlagBitsKHR(8);
    public static final VkVideoEncodeH265RateControlFlagBitsKHR VK_VIDEO_ENCODE_H265_RATE_CONTROL_REFERENCE_PATTERN_FLAT_BIT_KHR = new VkVideoEncodeH265RateControlFlagBitsKHR(4);
    public static final VkVideoEncodeH265RateControlFlagBitsKHR VK_VIDEO_ENCODE_H265_RATE_CONTROL_REGULAR_GOP_BIT_KHR = new VkVideoEncodeH265RateControlFlagBitsKHR(2);
    public static final VkVideoEncodeH265RateControlFlagBitsKHR VK_VIDEO_ENCODE_H265_RATE_CONTROL_TEMPORAL_SUB_LAYER_PATTERN_DYADIC_BIT_KHR = new VkVideoEncodeH265RateControlFlagBitsKHR(16);

    public static VkVideoEncodeH265RateControlFlagBitsKHR fromValue(int value) {
        return switch (value) {
            case 1 -> VK_VIDEO_ENCODE_H265_RATE_CONTROL_ATTEMPT_HRD_COMPLIANCE_BIT_KHR;
            case 2147483647 -> VK_VIDEO_ENCODE_H265_RATE_CONTROL_FLAG_BITS_MAX_ENUM_KHR;
            case 8 -> VK_VIDEO_ENCODE_H265_RATE_CONTROL_REFERENCE_PATTERN_DYADIC_BIT_KHR;
            case 4 -> VK_VIDEO_ENCODE_H265_RATE_CONTROL_REFERENCE_PATTERN_FLAT_BIT_KHR;
            case 2 -> VK_VIDEO_ENCODE_H265_RATE_CONTROL_REGULAR_GOP_BIT_KHR;
            case 16 -> VK_VIDEO_ENCODE_H265_RATE_CONTROL_TEMPORAL_SUB_LAYER_PATTERN_DYADIC_BIT_KHR;
            default -> new VkVideoEncodeH265RateControlFlagBitsKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
