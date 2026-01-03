package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkVideoDecodeH264PictureLayoutFlagBitsKHR
 * Generated from jextract bindings
 */
public record VkVideoDecodeH264PictureLayoutFlagBitsKHR(int value) {

    public static final VkVideoDecodeH264PictureLayoutFlagBitsKHR VK_VIDEO_DECODE_H264_PICTURE_LAYOUT_FLAG_BITS_MAX_ENUM_KHR = new VkVideoDecodeH264PictureLayoutFlagBitsKHR(2147483647);
    public static final VkVideoDecodeH264PictureLayoutFlagBitsKHR VK_VIDEO_DECODE_H264_PICTURE_LAYOUT_INTERLACED_INTERLEAVED_LINES_BIT_KHR = new VkVideoDecodeH264PictureLayoutFlagBitsKHR(1);
    public static final VkVideoDecodeH264PictureLayoutFlagBitsKHR VK_VIDEO_DECODE_H264_PICTURE_LAYOUT_INTERLACED_SEPARATE_PLANES_BIT_KHR = new VkVideoDecodeH264PictureLayoutFlagBitsKHR(2);
    public static final VkVideoDecodeH264PictureLayoutFlagBitsKHR VK_VIDEO_DECODE_H264_PICTURE_LAYOUT_PROGRESSIVE_KHR = new VkVideoDecodeH264PictureLayoutFlagBitsKHR(0);

    public static VkVideoDecodeH264PictureLayoutFlagBitsKHR fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_VIDEO_DECODE_H264_PICTURE_LAYOUT_FLAG_BITS_MAX_ENUM_KHR;
            case 1 -> VK_VIDEO_DECODE_H264_PICTURE_LAYOUT_INTERLACED_INTERLEAVED_LINES_BIT_KHR;
            case 2 -> VK_VIDEO_DECODE_H264_PICTURE_LAYOUT_INTERLACED_SEPARATE_PLANES_BIT_KHR;
            case 0 -> VK_VIDEO_DECODE_H264_PICTURE_LAYOUT_PROGRESSIVE_KHR;
            default -> new VkVideoDecodeH264PictureLayoutFlagBitsKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
