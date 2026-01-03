package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkVideoEncodeAV1RateControlGroupKHR
 * Generated from jextract bindings
 */
public record VkVideoEncodeAV1RateControlGroupKHR(int value) {

    public static final VkVideoEncodeAV1RateControlGroupKHR VK_VIDEO_ENCODE_AV1_RATE_CONTROL_GROUP_BIPREDICTIVE_KHR = new VkVideoEncodeAV1RateControlGroupKHR(2);
    public static final VkVideoEncodeAV1RateControlGroupKHR VK_VIDEO_ENCODE_AV1_RATE_CONTROL_GROUP_INTRA_KHR = new VkVideoEncodeAV1RateControlGroupKHR(0);
    public static final VkVideoEncodeAV1RateControlGroupKHR VK_VIDEO_ENCODE_AV1_RATE_CONTROL_GROUP_MAX_ENUM_KHR = new VkVideoEncodeAV1RateControlGroupKHR(2147483647);
    public static final VkVideoEncodeAV1RateControlGroupKHR VK_VIDEO_ENCODE_AV1_RATE_CONTROL_GROUP_PREDICTIVE_KHR = new VkVideoEncodeAV1RateControlGroupKHR(1);

    public static VkVideoEncodeAV1RateControlGroupKHR fromValue(int value) {
        return switch (value) {
            case 2 -> VK_VIDEO_ENCODE_AV1_RATE_CONTROL_GROUP_BIPREDICTIVE_KHR;
            case 0 -> VK_VIDEO_ENCODE_AV1_RATE_CONTROL_GROUP_INTRA_KHR;
            case 2147483647 -> VK_VIDEO_ENCODE_AV1_RATE_CONTROL_GROUP_MAX_ENUM_KHR;
            case 1 -> VK_VIDEO_ENCODE_AV1_RATE_CONTROL_GROUP_PREDICTIVE_KHR;
            default -> new VkVideoEncodeAV1RateControlGroupKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
