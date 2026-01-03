package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkVideoEncodeFlagBitsKHR
 * Generated from jextract bindings
 */
public record VkVideoEncodeFlagBitsKHR(int value) {

    public static final VkVideoEncodeFlagBitsKHR VK_VIDEO_ENCODE_FLAG_BITS_MAX_ENUM_KHR = new VkVideoEncodeFlagBitsKHR(2147483647);
    public static final VkVideoEncodeFlagBitsKHR VK_VIDEO_ENCODE_INTRA_REFRESH_BIT_KHR = new VkVideoEncodeFlagBitsKHR(4);
    public static final VkVideoEncodeFlagBitsKHR VK_VIDEO_ENCODE_WITH_EMPHASIS_MAP_BIT_KHR = new VkVideoEncodeFlagBitsKHR(2);
    public static final VkVideoEncodeFlagBitsKHR VK_VIDEO_ENCODE_WITH_QUANTIZATION_DELTA_MAP_BIT_KHR = new VkVideoEncodeFlagBitsKHR(1);

    public static VkVideoEncodeFlagBitsKHR fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_VIDEO_ENCODE_FLAG_BITS_MAX_ENUM_KHR;
            case 4 -> VK_VIDEO_ENCODE_INTRA_REFRESH_BIT_KHR;
            case 2 -> VK_VIDEO_ENCODE_WITH_EMPHASIS_MAP_BIT_KHR;
            case 1 -> VK_VIDEO_ENCODE_WITH_QUANTIZATION_DELTA_MAP_BIT_KHR;
            default -> new VkVideoEncodeFlagBitsKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
