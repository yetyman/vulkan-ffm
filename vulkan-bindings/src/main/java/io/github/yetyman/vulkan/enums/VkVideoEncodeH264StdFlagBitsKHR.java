package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkVideoEncodeH264StdFlagBitsKHR
 * Generated from jextract bindings
 */
public record VkVideoEncodeH264StdFlagBitsKHR(int value) {

    public static final VkVideoEncodeH264StdFlagBitsKHR VK_VIDEO_ENCODE_H264_STD_CHROMA_QP_INDEX_OFFSET_BIT_KHR = new VkVideoEncodeH264StdFlagBitsKHR(8);
    public static final VkVideoEncodeH264StdFlagBitsKHR VK_VIDEO_ENCODE_H264_STD_CONSTRAINED_INTRA_PRED_FLAG_SET_BIT_KHR = new VkVideoEncodeH264StdFlagBitsKHR(16384);
    public static final VkVideoEncodeH264StdFlagBitsKHR VK_VIDEO_ENCODE_H264_STD_DEBLOCKING_FILTER_DISABLED_BIT_KHR = new VkVideoEncodeH264StdFlagBitsKHR(32768);
    public static final VkVideoEncodeH264StdFlagBitsKHR VK_VIDEO_ENCODE_H264_STD_DEBLOCKING_FILTER_ENABLED_BIT_KHR = new VkVideoEncodeH264StdFlagBitsKHR(65536);
    public static final VkVideoEncodeH264StdFlagBitsKHR VK_VIDEO_ENCODE_H264_STD_DEBLOCKING_FILTER_PARTIAL_BIT_KHR = new VkVideoEncodeH264StdFlagBitsKHR(131072);
    public static final VkVideoEncodeH264StdFlagBitsKHR VK_VIDEO_ENCODE_H264_STD_DIFFERENT_SLICE_QP_DELTA_BIT_KHR = new VkVideoEncodeH264StdFlagBitsKHR(1048576);
    public static final VkVideoEncodeH264StdFlagBitsKHR VK_VIDEO_ENCODE_H264_STD_DIRECT_8X8_INFERENCE_FLAG_UNSET_BIT_KHR = new VkVideoEncodeH264StdFlagBitsKHR(8192);
    public static final VkVideoEncodeH264StdFlagBitsKHR VK_VIDEO_ENCODE_H264_STD_DIRECT_SPATIAL_MV_PRED_FLAG_UNSET_BIT_KHR = new VkVideoEncodeH264StdFlagBitsKHR(1024);
    public static final VkVideoEncodeH264StdFlagBitsKHR VK_VIDEO_ENCODE_H264_STD_ENTROPY_CODING_MODE_FLAG_SET_BIT_KHR = new VkVideoEncodeH264StdFlagBitsKHR(4096);
    public static final VkVideoEncodeH264StdFlagBitsKHR VK_VIDEO_ENCODE_H264_STD_ENTROPY_CODING_MODE_FLAG_UNSET_BIT_KHR = new VkVideoEncodeH264StdFlagBitsKHR(2048);
    public static final VkVideoEncodeH264StdFlagBitsKHR VK_VIDEO_ENCODE_H264_STD_FLAG_BITS_MAX_ENUM_KHR = new VkVideoEncodeH264StdFlagBitsKHR(2147483647);
    public static final VkVideoEncodeH264StdFlagBitsKHR VK_VIDEO_ENCODE_H264_STD_PIC_INIT_QP_MINUS26_BIT_KHR = new VkVideoEncodeH264StdFlagBitsKHR(32);
    public static final VkVideoEncodeH264StdFlagBitsKHR VK_VIDEO_ENCODE_H264_STD_QPPRIME_Y_ZERO_TRANSFORM_BYPASS_FLAG_SET_BIT_KHR = new VkVideoEncodeH264StdFlagBitsKHR(2);
    public static final VkVideoEncodeH264StdFlagBitsKHR VK_VIDEO_ENCODE_H264_STD_SCALING_MATRIX_PRESENT_FLAG_SET_BIT_KHR = new VkVideoEncodeH264StdFlagBitsKHR(4);
    public static final VkVideoEncodeH264StdFlagBitsKHR VK_VIDEO_ENCODE_H264_STD_SECOND_CHROMA_QP_INDEX_OFFSET_BIT_KHR = new VkVideoEncodeH264StdFlagBitsKHR(16);
    public static final VkVideoEncodeH264StdFlagBitsKHR VK_VIDEO_ENCODE_H264_STD_SEPARATE_COLOR_PLANE_FLAG_SET_BIT_KHR = new VkVideoEncodeH264StdFlagBitsKHR(1);
    public static final VkVideoEncodeH264StdFlagBitsKHR VK_VIDEO_ENCODE_H264_STD_SLICE_QP_DELTA_BIT_KHR = new VkVideoEncodeH264StdFlagBitsKHR(524288);
    public static final VkVideoEncodeH264StdFlagBitsKHR VK_VIDEO_ENCODE_H264_STD_TRANSFORM_8X8_MODE_FLAG_SET_BIT_KHR = new VkVideoEncodeH264StdFlagBitsKHR(512);
    public static final VkVideoEncodeH264StdFlagBitsKHR VK_VIDEO_ENCODE_H264_STD_WEIGHTED_BIPRED_IDC_EXPLICIT_BIT_KHR = new VkVideoEncodeH264StdFlagBitsKHR(128);
    public static final VkVideoEncodeH264StdFlagBitsKHR VK_VIDEO_ENCODE_H264_STD_WEIGHTED_BIPRED_IDC_IMPLICIT_BIT_KHR = new VkVideoEncodeH264StdFlagBitsKHR(256);
    public static final VkVideoEncodeH264StdFlagBitsKHR VK_VIDEO_ENCODE_H264_STD_WEIGHTED_PRED_FLAG_SET_BIT_KHR = new VkVideoEncodeH264StdFlagBitsKHR(64);

    public static VkVideoEncodeH264StdFlagBitsKHR fromValue(int value) {
        return switch (value) {
            case 8 -> VK_VIDEO_ENCODE_H264_STD_CHROMA_QP_INDEX_OFFSET_BIT_KHR;
            case 16384 -> VK_VIDEO_ENCODE_H264_STD_CONSTRAINED_INTRA_PRED_FLAG_SET_BIT_KHR;
            case 32768 -> VK_VIDEO_ENCODE_H264_STD_DEBLOCKING_FILTER_DISABLED_BIT_KHR;
            case 65536 -> VK_VIDEO_ENCODE_H264_STD_DEBLOCKING_FILTER_ENABLED_BIT_KHR;
            case 131072 -> VK_VIDEO_ENCODE_H264_STD_DEBLOCKING_FILTER_PARTIAL_BIT_KHR;
            case 1048576 -> VK_VIDEO_ENCODE_H264_STD_DIFFERENT_SLICE_QP_DELTA_BIT_KHR;
            case 8192 -> VK_VIDEO_ENCODE_H264_STD_DIRECT_8X8_INFERENCE_FLAG_UNSET_BIT_KHR;
            case 1024 -> VK_VIDEO_ENCODE_H264_STD_DIRECT_SPATIAL_MV_PRED_FLAG_UNSET_BIT_KHR;
            case 4096 -> VK_VIDEO_ENCODE_H264_STD_ENTROPY_CODING_MODE_FLAG_SET_BIT_KHR;
            case 2048 -> VK_VIDEO_ENCODE_H264_STD_ENTROPY_CODING_MODE_FLAG_UNSET_BIT_KHR;
            case 2147483647 -> VK_VIDEO_ENCODE_H264_STD_FLAG_BITS_MAX_ENUM_KHR;
            case 32 -> VK_VIDEO_ENCODE_H264_STD_PIC_INIT_QP_MINUS26_BIT_KHR;
            case 2 -> VK_VIDEO_ENCODE_H264_STD_QPPRIME_Y_ZERO_TRANSFORM_BYPASS_FLAG_SET_BIT_KHR;
            case 4 -> VK_VIDEO_ENCODE_H264_STD_SCALING_MATRIX_PRESENT_FLAG_SET_BIT_KHR;
            case 16 -> VK_VIDEO_ENCODE_H264_STD_SECOND_CHROMA_QP_INDEX_OFFSET_BIT_KHR;
            case 1 -> VK_VIDEO_ENCODE_H264_STD_SEPARATE_COLOR_PLANE_FLAG_SET_BIT_KHR;
            case 524288 -> VK_VIDEO_ENCODE_H264_STD_SLICE_QP_DELTA_BIT_KHR;
            case 512 -> VK_VIDEO_ENCODE_H264_STD_TRANSFORM_8X8_MODE_FLAG_SET_BIT_KHR;
            case 128 -> VK_VIDEO_ENCODE_H264_STD_WEIGHTED_BIPRED_IDC_EXPLICIT_BIT_KHR;
            case 256 -> VK_VIDEO_ENCODE_H264_STD_WEIGHTED_BIPRED_IDC_IMPLICIT_BIT_KHR;
            case 64 -> VK_VIDEO_ENCODE_H264_STD_WEIGHTED_PRED_FLAG_SET_BIT_KHR;
            default -> new VkVideoEncodeH264StdFlagBitsKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
