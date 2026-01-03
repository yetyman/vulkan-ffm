package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkVideoEncodeAV1CapabilityFlagBitsKHR
 * Generated from jextract bindings
 */
public record VkVideoEncodeAV1CapabilityFlagBitsKHR(int value) {

    public static final VkVideoEncodeAV1CapabilityFlagBitsKHR VK_VIDEO_ENCODE_AV1_CAPABILITY_COMPOUND_PREDICTION_INTRA_REFRESH_BIT_KHR = new VkVideoEncodeAV1CapabilityFlagBitsKHR(32);
    public static final VkVideoEncodeAV1CapabilityFlagBitsKHR VK_VIDEO_ENCODE_AV1_CAPABILITY_FLAG_BITS_MAX_ENUM_KHR = new VkVideoEncodeAV1CapabilityFlagBitsKHR(2147483647);
    public static final VkVideoEncodeAV1CapabilityFlagBitsKHR VK_VIDEO_ENCODE_AV1_CAPABILITY_FRAME_SIZE_OVERRIDE_BIT_KHR = new VkVideoEncodeAV1CapabilityFlagBitsKHR(8);
    public static final VkVideoEncodeAV1CapabilityFlagBitsKHR VK_VIDEO_ENCODE_AV1_CAPABILITY_GENERATE_OBU_EXTENSION_HEADER_BIT_KHR = new VkVideoEncodeAV1CapabilityFlagBitsKHR(2);
    public static final VkVideoEncodeAV1CapabilityFlagBitsKHR VK_VIDEO_ENCODE_AV1_CAPABILITY_MOTION_VECTOR_SCALING_BIT_KHR = new VkVideoEncodeAV1CapabilityFlagBitsKHR(16);
    public static final VkVideoEncodeAV1CapabilityFlagBitsKHR VK_VIDEO_ENCODE_AV1_CAPABILITY_PER_RATE_CONTROL_GROUP_MIN_MAX_Q_INDEX_BIT_KHR = new VkVideoEncodeAV1CapabilityFlagBitsKHR(1);
    public static final VkVideoEncodeAV1CapabilityFlagBitsKHR VK_VIDEO_ENCODE_AV1_CAPABILITY_PRIMARY_REFERENCE_CDF_ONLY_BIT_KHR = new VkVideoEncodeAV1CapabilityFlagBitsKHR(4);

    public static VkVideoEncodeAV1CapabilityFlagBitsKHR fromValue(int value) {
        return switch (value) {
            case 32 -> VK_VIDEO_ENCODE_AV1_CAPABILITY_COMPOUND_PREDICTION_INTRA_REFRESH_BIT_KHR;
            case 2147483647 -> VK_VIDEO_ENCODE_AV1_CAPABILITY_FLAG_BITS_MAX_ENUM_KHR;
            case 8 -> VK_VIDEO_ENCODE_AV1_CAPABILITY_FRAME_SIZE_OVERRIDE_BIT_KHR;
            case 2 -> VK_VIDEO_ENCODE_AV1_CAPABILITY_GENERATE_OBU_EXTENSION_HEADER_BIT_KHR;
            case 16 -> VK_VIDEO_ENCODE_AV1_CAPABILITY_MOTION_VECTOR_SCALING_BIT_KHR;
            case 1 -> VK_VIDEO_ENCODE_AV1_CAPABILITY_PER_RATE_CONTROL_GROUP_MIN_MAX_Q_INDEX_BIT_KHR;
            case 4 -> VK_VIDEO_ENCODE_AV1_CAPABILITY_PRIMARY_REFERENCE_CDF_ONLY_BIT_KHR;
            default -> new VkVideoEncodeAV1CapabilityFlagBitsKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
