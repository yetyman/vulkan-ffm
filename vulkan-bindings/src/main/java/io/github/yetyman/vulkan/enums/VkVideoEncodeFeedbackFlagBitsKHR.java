package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkVideoEncodeFeedbackFlagBitsKHR
 * Generated from jextract bindings
 */
public record VkVideoEncodeFeedbackFlagBitsKHR(int value) {

    public static final VkVideoEncodeFeedbackFlagBitsKHR VK_VIDEO_ENCODE_FEEDBACK_BITSTREAM_BUFFER_OFFSET_BIT_KHR = new VkVideoEncodeFeedbackFlagBitsKHR(1);
    public static final VkVideoEncodeFeedbackFlagBitsKHR VK_VIDEO_ENCODE_FEEDBACK_BITSTREAM_BYTES_WRITTEN_BIT_KHR = new VkVideoEncodeFeedbackFlagBitsKHR(2);
    public static final VkVideoEncodeFeedbackFlagBitsKHR VK_VIDEO_ENCODE_FEEDBACK_BITSTREAM_HAS_OVERRIDES_BIT_KHR = new VkVideoEncodeFeedbackFlagBitsKHR(4);
    public static final VkVideoEncodeFeedbackFlagBitsKHR VK_VIDEO_ENCODE_FEEDBACK_FLAG_BITS_MAX_ENUM_KHR = new VkVideoEncodeFeedbackFlagBitsKHR(2147483647);

    public static VkVideoEncodeFeedbackFlagBitsKHR fromValue(int value) {
        return switch (value) {
            case 1 -> VK_VIDEO_ENCODE_FEEDBACK_BITSTREAM_BUFFER_OFFSET_BIT_KHR;
            case 2 -> VK_VIDEO_ENCODE_FEEDBACK_BITSTREAM_BYTES_WRITTEN_BIT_KHR;
            case 4 -> VK_VIDEO_ENCODE_FEEDBACK_BITSTREAM_HAS_OVERRIDES_BIT_KHR;
            case 2147483647 -> VK_VIDEO_ENCODE_FEEDBACK_FLAG_BITS_MAX_ENUM_KHR;
            default -> new VkVideoEncodeFeedbackFlagBitsKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
