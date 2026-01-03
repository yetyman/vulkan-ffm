package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkVideoEncodeH265TransformBlockSizeFlagBitsKHR
 * Generated from jextract bindings
 */
public record VkVideoEncodeH265TransformBlockSizeFlagBitsKHR(int value) {

    public static final VkVideoEncodeH265TransformBlockSizeFlagBitsKHR VK_VIDEO_ENCODE_H265_TRANSFORM_BLOCK_SIZE_16_BIT_KHR = new VkVideoEncodeH265TransformBlockSizeFlagBitsKHR(4);
    public static final VkVideoEncodeH265TransformBlockSizeFlagBitsKHR VK_VIDEO_ENCODE_H265_TRANSFORM_BLOCK_SIZE_32_BIT_KHR = new VkVideoEncodeH265TransformBlockSizeFlagBitsKHR(8);
    public static final VkVideoEncodeH265TransformBlockSizeFlagBitsKHR VK_VIDEO_ENCODE_H265_TRANSFORM_BLOCK_SIZE_4_BIT_KHR = new VkVideoEncodeH265TransformBlockSizeFlagBitsKHR(1);
    public static final VkVideoEncodeH265TransformBlockSizeFlagBitsKHR VK_VIDEO_ENCODE_H265_TRANSFORM_BLOCK_SIZE_8_BIT_KHR = new VkVideoEncodeH265TransformBlockSizeFlagBitsKHR(2);
    public static final VkVideoEncodeH265TransformBlockSizeFlagBitsKHR VK_VIDEO_ENCODE_H265_TRANSFORM_BLOCK_SIZE_FLAG_BITS_MAX_ENUM_KHR = new VkVideoEncodeH265TransformBlockSizeFlagBitsKHR(2147483647);

    public static VkVideoEncodeH265TransformBlockSizeFlagBitsKHR fromValue(int value) {
        return switch (value) {
            case 4 -> VK_VIDEO_ENCODE_H265_TRANSFORM_BLOCK_SIZE_16_BIT_KHR;
            case 8 -> VK_VIDEO_ENCODE_H265_TRANSFORM_BLOCK_SIZE_32_BIT_KHR;
            case 1 -> VK_VIDEO_ENCODE_H265_TRANSFORM_BLOCK_SIZE_4_BIT_KHR;
            case 2 -> VK_VIDEO_ENCODE_H265_TRANSFORM_BLOCK_SIZE_8_BIT_KHR;
            case 2147483647 -> VK_VIDEO_ENCODE_H265_TRANSFORM_BLOCK_SIZE_FLAG_BITS_MAX_ENUM_KHR;
            default -> new VkVideoEncodeH265TransformBlockSizeFlagBitsKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
