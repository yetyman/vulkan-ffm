package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkVideoDecodeUsageFlagBitsKHR
 * Generated from jextract bindings
 */
public record VkVideoDecodeUsageFlagBitsKHR(int value) {

    public static final VkVideoDecodeUsageFlagBitsKHR VK_VIDEO_DECODE_USAGE_DEFAULT_KHR = new VkVideoDecodeUsageFlagBitsKHR(0);
    public static final VkVideoDecodeUsageFlagBitsKHR VK_VIDEO_DECODE_USAGE_FLAG_BITS_MAX_ENUM_KHR = new VkVideoDecodeUsageFlagBitsKHR(2147483647);
    public static final VkVideoDecodeUsageFlagBitsKHR VK_VIDEO_DECODE_USAGE_OFFLINE_BIT_KHR = new VkVideoDecodeUsageFlagBitsKHR(2);
    public static final VkVideoDecodeUsageFlagBitsKHR VK_VIDEO_DECODE_USAGE_STREAMING_BIT_KHR = new VkVideoDecodeUsageFlagBitsKHR(4);
    public static final VkVideoDecodeUsageFlagBitsKHR VK_VIDEO_DECODE_USAGE_TRANSCODING_BIT_KHR = new VkVideoDecodeUsageFlagBitsKHR(1);

    public static VkVideoDecodeUsageFlagBitsKHR fromValue(int value) {
        return switch (value) {
            case 0 -> VK_VIDEO_DECODE_USAGE_DEFAULT_KHR;
            case 2147483647 -> VK_VIDEO_DECODE_USAGE_FLAG_BITS_MAX_ENUM_KHR;
            case 2 -> VK_VIDEO_DECODE_USAGE_OFFLINE_BIT_KHR;
            case 4 -> VK_VIDEO_DECODE_USAGE_STREAMING_BIT_KHR;
            case 1 -> VK_VIDEO_DECODE_USAGE_TRANSCODING_BIT_KHR;
            default -> new VkVideoDecodeUsageFlagBitsKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
