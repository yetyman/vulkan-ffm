package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkVideoEncodeH265StdFlagBitsKHR
 * Generated from jextract bindings
 */
public record VkVideoEncodeH265StdFlagBitsKHR(int value) {

    public static final VkVideoEncodeH265StdFlagBitsKHR VK_VIDEO_ENCODE_H265_STD_CONSTRAINED_INTRA_PRED_FLAG_SET_BIT_KHR = new VkVideoEncodeH265StdFlagBitsKHR(16384);
    public static final VkVideoEncodeH265StdFlagBitsKHR VK_VIDEO_ENCODE_H265_STD_DEBLOCKING_FILTER_OVERRIDE_ENABLED_FLAG_SET_BIT_KHR = new VkVideoEncodeH265StdFlagBitsKHR(65536);
    public static final VkVideoEncodeH265StdFlagBitsKHR VK_VIDEO_ENCODE_H265_STD_DEPENDENT_SLICE_SEGMENTS_ENABLED_FLAG_SET_BIT_KHR = new VkVideoEncodeH265StdFlagBitsKHR(131072);
    public static final VkVideoEncodeH265StdFlagBitsKHR VK_VIDEO_ENCODE_H265_STD_DEPENDENT_SLICE_SEGMENT_FLAG_SET_BIT_KHR = new VkVideoEncodeH265StdFlagBitsKHR(262144);
    public static final VkVideoEncodeH265StdFlagBitsKHR VK_VIDEO_ENCODE_H265_STD_DIFFERENT_SLICE_QP_DELTA_BIT_KHR = new VkVideoEncodeH265StdFlagBitsKHR(1048576);
    public static final VkVideoEncodeH265StdFlagBitsKHR VK_VIDEO_ENCODE_H265_STD_ENTROPY_CODING_SYNC_ENABLED_FLAG_SET_BIT_KHR = new VkVideoEncodeH265StdFlagBitsKHR(32768);
    public static final VkVideoEncodeH265StdFlagBitsKHR VK_VIDEO_ENCODE_H265_STD_FLAG_BITS_MAX_ENUM_KHR = new VkVideoEncodeH265StdFlagBitsKHR(2147483647);
    public static final VkVideoEncodeH265StdFlagBitsKHR VK_VIDEO_ENCODE_H265_STD_INIT_QP_MINUS26_BIT_KHR = new VkVideoEncodeH265StdFlagBitsKHR(32);
    public static final VkVideoEncodeH265StdFlagBitsKHR VK_VIDEO_ENCODE_H265_STD_LOG2_PARALLEL_MERGE_LEVEL_MINUS2_BIT_KHR = new VkVideoEncodeH265StdFlagBitsKHR(256);
    public static final VkVideoEncodeH265StdFlagBitsKHR VK_VIDEO_ENCODE_H265_STD_PCM_ENABLED_FLAG_SET_BIT_KHR = new VkVideoEncodeH265StdFlagBitsKHR(8);
    public static final VkVideoEncodeH265StdFlagBitsKHR VK_VIDEO_ENCODE_H265_STD_PPS_SLICE_CHROMA_QP_OFFSETS_PRESENT_FLAG_SET_BIT_KHR = new VkVideoEncodeH265StdFlagBitsKHR(4096);
    public static final VkVideoEncodeH265StdFlagBitsKHR VK_VIDEO_ENCODE_H265_STD_SAMPLE_ADAPTIVE_OFFSET_ENABLED_FLAG_SET_BIT_KHR = new VkVideoEncodeH265StdFlagBitsKHR(2);
    public static final VkVideoEncodeH265StdFlagBitsKHR VK_VIDEO_ENCODE_H265_STD_SCALING_LIST_DATA_PRESENT_FLAG_SET_BIT_KHR = new VkVideoEncodeH265StdFlagBitsKHR(4);
    public static final VkVideoEncodeH265StdFlagBitsKHR VK_VIDEO_ENCODE_H265_STD_SEPARATE_COLOR_PLANE_FLAG_SET_BIT_KHR = new VkVideoEncodeH265StdFlagBitsKHR(1);
    public static final VkVideoEncodeH265StdFlagBitsKHR VK_VIDEO_ENCODE_H265_STD_SIGN_DATA_HIDING_ENABLED_FLAG_SET_BIT_KHR = new VkVideoEncodeH265StdFlagBitsKHR(512);
    public static final VkVideoEncodeH265StdFlagBitsKHR VK_VIDEO_ENCODE_H265_STD_SLICE_QP_DELTA_BIT_KHR = new VkVideoEncodeH265StdFlagBitsKHR(524288);
    public static final VkVideoEncodeH265StdFlagBitsKHR VK_VIDEO_ENCODE_H265_STD_SPS_TEMPORAL_MVP_ENABLED_FLAG_SET_BIT_KHR = new VkVideoEncodeH265StdFlagBitsKHR(16);
    public static final VkVideoEncodeH265StdFlagBitsKHR VK_VIDEO_ENCODE_H265_STD_TRANSFORM_SKIP_ENABLED_FLAG_SET_BIT_KHR = new VkVideoEncodeH265StdFlagBitsKHR(1024);
    public static final VkVideoEncodeH265StdFlagBitsKHR VK_VIDEO_ENCODE_H265_STD_TRANSFORM_SKIP_ENABLED_FLAG_UNSET_BIT_KHR = new VkVideoEncodeH265StdFlagBitsKHR(2048);
    public static final VkVideoEncodeH265StdFlagBitsKHR VK_VIDEO_ENCODE_H265_STD_TRANSQUANT_BYPASS_ENABLED_FLAG_SET_BIT_KHR = new VkVideoEncodeH265StdFlagBitsKHR(8192);
    public static final VkVideoEncodeH265StdFlagBitsKHR VK_VIDEO_ENCODE_H265_STD_WEIGHTED_BIPRED_FLAG_SET_BIT_KHR = new VkVideoEncodeH265StdFlagBitsKHR(128);
    public static final VkVideoEncodeH265StdFlagBitsKHR VK_VIDEO_ENCODE_H265_STD_WEIGHTED_PRED_FLAG_SET_BIT_KHR = new VkVideoEncodeH265StdFlagBitsKHR(64);

    public static VkVideoEncodeH265StdFlagBitsKHR fromValue(int value) {
        return switch (value) {
            case 16384 -> VK_VIDEO_ENCODE_H265_STD_CONSTRAINED_INTRA_PRED_FLAG_SET_BIT_KHR;
            case 65536 -> VK_VIDEO_ENCODE_H265_STD_DEBLOCKING_FILTER_OVERRIDE_ENABLED_FLAG_SET_BIT_KHR;
            case 131072 -> VK_VIDEO_ENCODE_H265_STD_DEPENDENT_SLICE_SEGMENTS_ENABLED_FLAG_SET_BIT_KHR;
            case 262144 -> VK_VIDEO_ENCODE_H265_STD_DEPENDENT_SLICE_SEGMENT_FLAG_SET_BIT_KHR;
            case 1048576 -> VK_VIDEO_ENCODE_H265_STD_DIFFERENT_SLICE_QP_DELTA_BIT_KHR;
            case 32768 -> VK_VIDEO_ENCODE_H265_STD_ENTROPY_CODING_SYNC_ENABLED_FLAG_SET_BIT_KHR;
            case 2147483647 -> VK_VIDEO_ENCODE_H265_STD_FLAG_BITS_MAX_ENUM_KHR;
            case 32 -> VK_VIDEO_ENCODE_H265_STD_INIT_QP_MINUS26_BIT_KHR;
            case 256 -> VK_VIDEO_ENCODE_H265_STD_LOG2_PARALLEL_MERGE_LEVEL_MINUS2_BIT_KHR;
            case 8 -> VK_VIDEO_ENCODE_H265_STD_PCM_ENABLED_FLAG_SET_BIT_KHR;
            case 4096 -> VK_VIDEO_ENCODE_H265_STD_PPS_SLICE_CHROMA_QP_OFFSETS_PRESENT_FLAG_SET_BIT_KHR;
            case 2 -> VK_VIDEO_ENCODE_H265_STD_SAMPLE_ADAPTIVE_OFFSET_ENABLED_FLAG_SET_BIT_KHR;
            case 4 -> VK_VIDEO_ENCODE_H265_STD_SCALING_LIST_DATA_PRESENT_FLAG_SET_BIT_KHR;
            case 1 -> VK_VIDEO_ENCODE_H265_STD_SEPARATE_COLOR_PLANE_FLAG_SET_BIT_KHR;
            case 512 -> VK_VIDEO_ENCODE_H265_STD_SIGN_DATA_HIDING_ENABLED_FLAG_SET_BIT_KHR;
            case 524288 -> VK_VIDEO_ENCODE_H265_STD_SLICE_QP_DELTA_BIT_KHR;
            case 16 -> VK_VIDEO_ENCODE_H265_STD_SPS_TEMPORAL_MVP_ENABLED_FLAG_SET_BIT_KHR;
            case 1024 -> VK_VIDEO_ENCODE_H265_STD_TRANSFORM_SKIP_ENABLED_FLAG_SET_BIT_KHR;
            case 2048 -> VK_VIDEO_ENCODE_H265_STD_TRANSFORM_SKIP_ENABLED_FLAG_UNSET_BIT_KHR;
            case 8192 -> VK_VIDEO_ENCODE_H265_STD_TRANSQUANT_BYPASS_ENABLED_FLAG_SET_BIT_KHR;
            case 128 -> VK_VIDEO_ENCODE_H265_STD_WEIGHTED_BIPRED_FLAG_SET_BIT_KHR;
            case 64 -> VK_VIDEO_ENCODE_H265_STD_WEIGHTED_PRED_FLAG_SET_BIT_KHR;
            default -> new VkVideoEncodeH265StdFlagBitsKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
