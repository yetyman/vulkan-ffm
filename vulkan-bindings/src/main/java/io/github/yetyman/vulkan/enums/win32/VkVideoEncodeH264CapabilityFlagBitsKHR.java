package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkVideoEncodeH264CapabilityFlagBitsKHR
 * Generated from jextract bindings
 */
public record VkVideoEncodeH264CapabilityFlagBitsKHR(int value) {

    public static final VkVideoEncodeH264CapabilityFlagBitsKHR VK_VIDEO_ENCODE_H264_CAPABILITY_B_FRAME_IN_L0_LIST_BIT_KHR = new VkVideoEncodeH264CapabilityFlagBitsKHR(16);
    public static final VkVideoEncodeH264CapabilityFlagBitsKHR VK_VIDEO_ENCODE_H264_CAPABILITY_B_FRAME_IN_L1_LIST_BIT_KHR = new VkVideoEncodeH264CapabilityFlagBitsKHR(32);
    public static final VkVideoEncodeH264CapabilityFlagBitsKHR VK_VIDEO_ENCODE_H264_CAPABILITY_B_PICTURE_INTRA_REFRESH_BIT_KHR = new VkVideoEncodeH264CapabilityFlagBitsKHR(1024);
    public static final VkVideoEncodeH264CapabilityFlagBitsKHR VK_VIDEO_ENCODE_H264_CAPABILITY_DIFFERENT_SLICE_TYPE_BIT_KHR = new VkVideoEncodeH264CapabilityFlagBitsKHR(8);
    public static final VkVideoEncodeH264CapabilityFlagBitsKHR VK_VIDEO_ENCODE_H264_CAPABILITY_FLAG_BITS_MAX_ENUM_KHR = new VkVideoEncodeH264CapabilityFlagBitsKHR(2147483647);
    public static final VkVideoEncodeH264CapabilityFlagBitsKHR VK_VIDEO_ENCODE_H264_CAPABILITY_GENERATE_PREFIX_NALU_BIT_KHR = new VkVideoEncodeH264CapabilityFlagBitsKHR(256);
    public static final VkVideoEncodeH264CapabilityFlagBitsKHR VK_VIDEO_ENCODE_H264_CAPABILITY_HRD_COMPLIANCE_BIT_KHR = new VkVideoEncodeH264CapabilityFlagBitsKHR(1);
    public static final VkVideoEncodeH264CapabilityFlagBitsKHR VK_VIDEO_ENCODE_H264_CAPABILITY_MB_QP_DIFF_WRAPAROUND_BIT_KHR = new VkVideoEncodeH264CapabilityFlagBitsKHR(512);
    public static final VkVideoEncodeH264CapabilityFlagBitsKHR VK_VIDEO_ENCODE_H264_CAPABILITY_PER_PICTURE_TYPE_MIN_MAX_QP_BIT_KHR = new VkVideoEncodeH264CapabilityFlagBitsKHR(64);
    public static final VkVideoEncodeH264CapabilityFlagBitsKHR VK_VIDEO_ENCODE_H264_CAPABILITY_PER_SLICE_CONSTANT_QP_BIT_KHR = new VkVideoEncodeH264CapabilityFlagBitsKHR(128);
    public static final VkVideoEncodeH264CapabilityFlagBitsKHR VK_VIDEO_ENCODE_H264_CAPABILITY_PREDICTION_WEIGHT_TABLE_GENERATED_BIT_KHR = new VkVideoEncodeH264CapabilityFlagBitsKHR(2);
    public static final VkVideoEncodeH264CapabilityFlagBitsKHR VK_VIDEO_ENCODE_H264_CAPABILITY_ROW_UNALIGNED_SLICE_BIT_KHR = new VkVideoEncodeH264CapabilityFlagBitsKHR(4);

    public static VkVideoEncodeH264CapabilityFlagBitsKHR fromValue(int value) {
        return switch (value) {
            case 16 -> VK_VIDEO_ENCODE_H264_CAPABILITY_B_FRAME_IN_L0_LIST_BIT_KHR;
            case 32 -> VK_VIDEO_ENCODE_H264_CAPABILITY_B_FRAME_IN_L1_LIST_BIT_KHR;
            case 1024 -> VK_VIDEO_ENCODE_H264_CAPABILITY_B_PICTURE_INTRA_REFRESH_BIT_KHR;
            case 8 -> VK_VIDEO_ENCODE_H264_CAPABILITY_DIFFERENT_SLICE_TYPE_BIT_KHR;
            case 2147483647 -> VK_VIDEO_ENCODE_H264_CAPABILITY_FLAG_BITS_MAX_ENUM_KHR;
            case 256 -> VK_VIDEO_ENCODE_H264_CAPABILITY_GENERATE_PREFIX_NALU_BIT_KHR;
            case 1 -> VK_VIDEO_ENCODE_H264_CAPABILITY_HRD_COMPLIANCE_BIT_KHR;
            case 512 -> VK_VIDEO_ENCODE_H264_CAPABILITY_MB_QP_DIFF_WRAPAROUND_BIT_KHR;
            case 64 -> VK_VIDEO_ENCODE_H264_CAPABILITY_PER_PICTURE_TYPE_MIN_MAX_QP_BIT_KHR;
            case 128 -> VK_VIDEO_ENCODE_H264_CAPABILITY_PER_SLICE_CONSTANT_QP_BIT_KHR;
            case 2 -> VK_VIDEO_ENCODE_H264_CAPABILITY_PREDICTION_WEIGHT_TABLE_GENERATED_BIT_KHR;
            case 4 -> VK_VIDEO_ENCODE_H264_CAPABILITY_ROW_UNALIGNED_SLICE_BIT_KHR;
            default -> new VkVideoEncodeH264CapabilityFlagBitsKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
