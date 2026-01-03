package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkVideoEncodeAV1PredictionModeKHR
 * Generated from jextract bindings
 */
public record VkVideoEncodeAV1PredictionModeKHR(int value) {

    public static final VkVideoEncodeAV1PredictionModeKHR VK_VIDEO_ENCODE_AV1_PREDICTION_MODE_BIDIRECTIONAL_COMPOUND_KHR = new VkVideoEncodeAV1PredictionModeKHR(3);
    public static final VkVideoEncodeAV1PredictionModeKHR VK_VIDEO_ENCODE_AV1_PREDICTION_MODE_INTRA_ONLY_KHR = new VkVideoEncodeAV1PredictionModeKHR(0);
    public static final VkVideoEncodeAV1PredictionModeKHR VK_VIDEO_ENCODE_AV1_PREDICTION_MODE_MAX_ENUM_KHR = new VkVideoEncodeAV1PredictionModeKHR(2147483647);
    public static final VkVideoEncodeAV1PredictionModeKHR VK_VIDEO_ENCODE_AV1_PREDICTION_MODE_SINGLE_REFERENCE_KHR = new VkVideoEncodeAV1PredictionModeKHR(1);
    public static final VkVideoEncodeAV1PredictionModeKHR VK_VIDEO_ENCODE_AV1_PREDICTION_MODE_UNIDIRECTIONAL_COMPOUND_KHR = new VkVideoEncodeAV1PredictionModeKHR(2);

    public static VkVideoEncodeAV1PredictionModeKHR fromValue(int value) {
        return switch (value) {
            case 3 -> VK_VIDEO_ENCODE_AV1_PREDICTION_MODE_BIDIRECTIONAL_COMPOUND_KHR;
            case 0 -> VK_VIDEO_ENCODE_AV1_PREDICTION_MODE_INTRA_ONLY_KHR;
            case 2147483647 -> VK_VIDEO_ENCODE_AV1_PREDICTION_MODE_MAX_ENUM_KHR;
            case 1 -> VK_VIDEO_ENCODE_AV1_PREDICTION_MODE_SINGLE_REFERENCE_KHR;
            case 2 -> VK_VIDEO_ENCODE_AV1_PREDICTION_MODE_UNIDIRECTIONAL_COMPOUND_KHR;
            default -> new VkVideoEncodeAV1PredictionModeKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
