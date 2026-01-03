package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkVideoEncodeRateControlModeFlagBitsKHR
 * Generated from jextract bindings
 */
public record VkVideoEncodeRateControlModeFlagBitsKHR(int value) {

    public static final VkVideoEncodeRateControlModeFlagBitsKHR VK_VIDEO_ENCODE_RATE_CONTROL_MODE_CBR_BIT_KHR = new VkVideoEncodeRateControlModeFlagBitsKHR(2);
    public static final VkVideoEncodeRateControlModeFlagBitsKHR VK_VIDEO_ENCODE_RATE_CONTROL_MODE_DEFAULT_KHR = new VkVideoEncodeRateControlModeFlagBitsKHR(0);
    public static final VkVideoEncodeRateControlModeFlagBitsKHR VK_VIDEO_ENCODE_RATE_CONTROL_MODE_DISABLED_BIT_KHR = new VkVideoEncodeRateControlModeFlagBitsKHR(1);
    public static final VkVideoEncodeRateControlModeFlagBitsKHR VK_VIDEO_ENCODE_RATE_CONTROL_MODE_FLAG_BITS_MAX_ENUM_KHR = new VkVideoEncodeRateControlModeFlagBitsKHR(2147483647);
    public static final VkVideoEncodeRateControlModeFlagBitsKHR VK_VIDEO_ENCODE_RATE_CONTROL_MODE_VBR_BIT_KHR = new VkVideoEncodeRateControlModeFlagBitsKHR(4);

    public static VkVideoEncodeRateControlModeFlagBitsKHR fromValue(int value) {
        return switch (value) {
            case 2 -> VK_VIDEO_ENCODE_RATE_CONTROL_MODE_CBR_BIT_KHR;
            case 0 -> VK_VIDEO_ENCODE_RATE_CONTROL_MODE_DEFAULT_KHR;
            case 1 -> VK_VIDEO_ENCODE_RATE_CONTROL_MODE_DISABLED_BIT_KHR;
            case 2147483647 -> VK_VIDEO_ENCODE_RATE_CONTROL_MODE_FLAG_BITS_MAX_ENUM_KHR;
            case 4 -> VK_VIDEO_ENCODE_RATE_CONTROL_MODE_VBR_BIT_KHR;
            default -> new VkVideoEncodeRateControlModeFlagBitsKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
