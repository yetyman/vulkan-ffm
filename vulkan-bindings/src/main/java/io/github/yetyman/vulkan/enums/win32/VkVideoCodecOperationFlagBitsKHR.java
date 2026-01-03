package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkVideoCodecOperationFlagBitsKHR
 * Generated from jextract bindings
 */
public record VkVideoCodecOperationFlagBitsKHR(int value) {

    public static final VkVideoCodecOperationFlagBitsKHR VK_VIDEO_CODEC_OPERATION_DECODE_AV1_BIT_KHR = new VkVideoCodecOperationFlagBitsKHR(4);
    public static final VkVideoCodecOperationFlagBitsKHR VK_VIDEO_CODEC_OPERATION_DECODE_H264_BIT_KHR = new VkVideoCodecOperationFlagBitsKHR(1);
    public static final VkVideoCodecOperationFlagBitsKHR VK_VIDEO_CODEC_OPERATION_DECODE_H265_BIT_KHR = new VkVideoCodecOperationFlagBitsKHR(2);
    public static final VkVideoCodecOperationFlagBitsKHR VK_VIDEO_CODEC_OPERATION_DECODE_VP9_BIT_KHR = new VkVideoCodecOperationFlagBitsKHR(8);
    public static final VkVideoCodecOperationFlagBitsKHR VK_VIDEO_CODEC_OPERATION_ENCODE_AV1_BIT_KHR = new VkVideoCodecOperationFlagBitsKHR(262144);
    public static final VkVideoCodecOperationFlagBitsKHR VK_VIDEO_CODEC_OPERATION_ENCODE_H264_BIT_KHR = new VkVideoCodecOperationFlagBitsKHR(65536);
    public static final VkVideoCodecOperationFlagBitsKHR VK_VIDEO_CODEC_OPERATION_ENCODE_H265_BIT_KHR = new VkVideoCodecOperationFlagBitsKHR(131072);
    public static final VkVideoCodecOperationFlagBitsKHR VK_VIDEO_CODEC_OPERATION_FLAG_BITS_MAX_ENUM_KHR = new VkVideoCodecOperationFlagBitsKHR(2147483647);
    public static final VkVideoCodecOperationFlagBitsKHR VK_VIDEO_CODEC_OPERATION_NONE_KHR = new VkVideoCodecOperationFlagBitsKHR(0);

    public static VkVideoCodecOperationFlagBitsKHR fromValue(int value) {
        return switch (value) {
            case 4 -> VK_VIDEO_CODEC_OPERATION_DECODE_AV1_BIT_KHR;
            case 1 -> VK_VIDEO_CODEC_OPERATION_DECODE_H264_BIT_KHR;
            case 2 -> VK_VIDEO_CODEC_OPERATION_DECODE_H265_BIT_KHR;
            case 8 -> VK_VIDEO_CODEC_OPERATION_DECODE_VP9_BIT_KHR;
            case 262144 -> VK_VIDEO_CODEC_OPERATION_ENCODE_AV1_BIT_KHR;
            case 65536 -> VK_VIDEO_CODEC_OPERATION_ENCODE_H264_BIT_KHR;
            case 131072 -> VK_VIDEO_CODEC_OPERATION_ENCODE_H265_BIT_KHR;
            case 2147483647 -> VK_VIDEO_CODEC_OPERATION_FLAG_BITS_MAX_ENUM_KHR;
            case 0 -> VK_VIDEO_CODEC_OPERATION_NONE_KHR;
            default -> new VkVideoCodecOperationFlagBitsKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
