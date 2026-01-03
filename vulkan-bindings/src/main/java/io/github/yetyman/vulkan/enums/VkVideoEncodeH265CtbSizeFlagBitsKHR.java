package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkVideoEncodeH265CtbSizeFlagBitsKHR
 * Generated from jextract bindings
 */
public record VkVideoEncodeH265CtbSizeFlagBitsKHR(int value) {

    public static final VkVideoEncodeH265CtbSizeFlagBitsKHR VK_VIDEO_ENCODE_H265_CTB_SIZE_16_BIT_KHR = new VkVideoEncodeH265CtbSizeFlagBitsKHR(1);
    public static final VkVideoEncodeH265CtbSizeFlagBitsKHR VK_VIDEO_ENCODE_H265_CTB_SIZE_32_BIT_KHR = new VkVideoEncodeH265CtbSizeFlagBitsKHR(2);
    public static final VkVideoEncodeH265CtbSizeFlagBitsKHR VK_VIDEO_ENCODE_H265_CTB_SIZE_64_BIT_KHR = new VkVideoEncodeH265CtbSizeFlagBitsKHR(4);
    public static final VkVideoEncodeH265CtbSizeFlagBitsKHR VK_VIDEO_ENCODE_H265_CTB_SIZE_FLAG_BITS_MAX_ENUM_KHR = new VkVideoEncodeH265CtbSizeFlagBitsKHR(2147483647);

    public static VkVideoEncodeH265CtbSizeFlagBitsKHR fromValue(int value) {
        return switch (value) {
            case 1 -> VK_VIDEO_ENCODE_H265_CTB_SIZE_16_BIT_KHR;
            case 2 -> VK_VIDEO_ENCODE_H265_CTB_SIZE_32_BIT_KHR;
            case 4 -> VK_VIDEO_ENCODE_H265_CTB_SIZE_64_BIT_KHR;
            case 2147483647 -> VK_VIDEO_ENCODE_H265_CTB_SIZE_FLAG_BITS_MAX_ENUM_KHR;
            default -> new VkVideoEncodeH265CtbSizeFlagBitsKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
