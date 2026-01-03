package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkVideoEncodeTuningModeKHR
 * Generated from jextract bindings
 */
public record VkVideoEncodeTuningModeKHR(int value) {

    public static final VkVideoEncodeTuningModeKHR VK_VIDEO_ENCODE_TUNING_MODE_DEFAULT_KHR = new VkVideoEncodeTuningModeKHR(0);
    public static final VkVideoEncodeTuningModeKHR VK_VIDEO_ENCODE_TUNING_MODE_HIGH_QUALITY_KHR = new VkVideoEncodeTuningModeKHR(1);
    public static final VkVideoEncodeTuningModeKHR VK_VIDEO_ENCODE_TUNING_MODE_LOSSLESS_KHR = new VkVideoEncodeTuningModeKHR(4);
    public static final VkVideoEncodeTuningModeKHR VK_VIDEO_ENCODE_TUNING_MODE_LOW_LATENCY_KHR = new VkVideoEncodeTuningModeKHR(2);
    public static final VkVideoEncodeTuningModeKHR VK_VIDEO_ENCODE_TUNING_MODE_MAX_ENUM_KHR = new VkVideoEncodeTuningModeKHR(2147483647);
    public static final VkVideoEncodeTuningModeKHR VK_VIDEO_ENCODE_TUNING_MODE_ULTRA_LOW_LATENCY_KHR = new VkVideoEncodeTuningModeKHR(3);

    public static VkVideoEncodeTuningModeKHR fromValue(int value) {
        return switch (value) {
            case 0 -> VK_VIDEO_ENCODE_TUNING_MODE_DEFAULT_KHR;
            case 1 -> VK_VIDEO_ENCODE_TUNING_MODE_HIGH_QUALITY_KHR;
            case 4 -> VK_VIDEO_ENCODE_TUNING_MODE_LOSSLESS_KHR;
            case 2 -> VK_VIDEO_ENCODE_TUNING_MODE_LOW_LATENCY_KHR;
            case 2147483647 -> VK_VIDEO_ENCODE_TUNING_MODE_MAX_ENUM_KHR;
            case 3 -> VK_VIDEO_ENCODE_TUNING_MODE_ULTRA_LOW_LATENCY_KHR;
            default -> new VkVideoEncodeTuningModeKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
