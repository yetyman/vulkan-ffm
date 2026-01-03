package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkVideoEncodeUsageFlagBitsKHR
 * Generated from jextract bindings
 */
public record VkVideoEncodeUsageFlagBitsKHR(int value) {

    public static final VkVideoEncodeUsageFlagBitsKHR VK_VIDEO_ENCODE_USAGE_CONFERENCING_BIT_KHR = new VkVideoEncodeUsageFlagBitsKHR(8);
    public static final VkVideoEncodeUsageFlagBitsKHR VK_VIDEO_ENCODE_USAGE_DEFAULT_KHR = new VkVideoEncodeUsageFlagBitsKHR(0);
    public static final VkVideoEncodeUsageFlagBitsKHR VK_VIDEO_ENCODE_USAGE_FLAG_BITS_MAX_ENUM_KHR = new VkVideoEncodeUsageFlagBitsKHR(2147483647);
    public static final VkVideoEncodeUsageFlagBitsKHR VK_VIDEO_ENCODE_USAGE_RECORDING_BIT_KHR = new VkVideoEncodeUsageFlagBitsKHR(4);
    public static final VkVideoEncodeUsageFlagBitsKHR VK_VIDEO_ENCODE_USAGE_STREAMING_BIT_KHR = new VkVideoEncodeUsageFlagBitsKHR(2);
    public static final VkVideoEncodeUsageFlagBitsKHR VK_VIDEO_ENCODE_USAGE_TRANSCODING_BIT_KHR = new VkVideoEncodeUsageFlagBitsKHR(1);

    public static VkVideoEncodeUsageFlagBitsKHR fromValue(int value) {
        return switch (value) {
            case 8 -> VK_VIDEO_ENCODE_USAGE_CONFERENCING_BIT_KHR;
            case 0 -> VK_VIDEO_ENCODE_USAGE_DEFAULT_KHR;
            case 2147483647 -> VK_VIDEO_ENCODE_USAGE_FLAG_BITS_MAX_ENUM_KHR;
            case 4 -> VK_VIDEO_ENCODE_USAGE_RECORDING_BIT_KHR;
            case 2 -> VK_VIDEO_ENCODE_USAGE_STREAMING_BIT_KHR;
            case 1 -> VK_VIDEO_ENCODE_USAGE_TRANSCODING_BIT_KHR;
            default -> new VkVideoEncodeUsageFlagBitsKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
