package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkVideoEncodeAV1RateControlFlagBitsKHR
 * Generated from jextract bindings
 */
public record VkVideoEncodeAV1RateControlFlagBitsKHR(int value) {

    public static final VkVideoEncodeAV1RateControlFlagBitsKHR VK_VIDEO_ENCODE_AV1_RATE_CONTROL_FLAG_BITS_MAX_ENUM_KHR = new VkVideoEncodeAV1RateControlFlagBitsKHR(2147483647);
    public static final VkVideoEncodeAV1RateControlFlagBitsKHR VK_VIDEO_ENCODE_AV1_RATE_CONTROL_REFERENCE_PATTERN_DYADIC_BIT_KHR = new VkVideoEncodeAV1RateControlFlagBitsKHR(8);
    public static final VkVideoEncodeAV1RateControlFlagBitsKHR VK_VIDEO_ENCODE_AV1_RATE_CONTROL_REFERENCE_PATTERN_FLAT_BIT_KHR = new VkVideoEncodeAV1RateControlFlagBitsKHR(4);
    public static final VkVideoEncodeAV1RateControlFlagBitsKHR VK_VIDEO_ENCODE_AV1_RATE_CONTROL_REGULAR_GOP_BIT_KHR = new VkVideoEncodeAV1RateControlFlagBitsKHR(1);
    public static final VkVideoEncodeAV1RateControlFlagBitsKHR VK_VIDEO_ENCODE_AV1_RATE_CONTROL_TEMPORAL_LAYER_PATTERN_DYADIC_BIT_KHR = new VkVideoEncodeAV1RateControlFlagBitsKHR(2);

    public static VkVideoEncodeAV1RateControlFlagBitsKHR fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_VIDEO_ENCODE_AV1_RATE_CONTROL_FLAG_BITS_MAX_ENUM_KHR;
            case 8 -> VK_VIDEO_ENCODE_AV1_RATE_CONTROL_REFERENCE_PATTERN_DYADIC_BIT_KHR;
            case 4 -> VK_VIDEO_ENCODE_AV1_RATE_CONTROL_REFERENCE_PATTERN_FLAT_BIT_KHR;
            case 1 -> VK_VIDEO_ENCODE_AV1_RATE_CONTROL_REGULAR_GOP_BIT_KHR;
            case 2 -> VK_VIDEO_ENCODE_AV1_RATE_CONTROL_TEMPORAL_LAYER_PATTERN_DYADIC_BIT_KHR;
            default -> new VkVideoEncodeAV1RateControlFlagBitsKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
