package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkVideoEncodeAV1StdFlagBitsKHR
 * Generated from jextract bindings
 */
public record VkVideoEncodeAV1StdFlagBitsKHR(int value) {

    public static final VkVideoEncodeAV1StdFlagBitsKHR VK_VIDEO_ENCODE_AV1_STD_DELTA_Q_BIT_KHR = new VkVideoEncodeAV1StdFlagBitsKHR(8);
    public static final VkVideoEncodeAV1StdFlagBitsKHR VK_VIDEO_ENCODE_AV1_STD_FLAG_BITS_MAX_ENUM_KHR = new VkVideoEncodeAV1StdFlagBitsKHR(2147483647);
    public static final VkVideoEncodeAV1StdFlagBitsKHR VK_VIDEO_ENCODE_AV1_STD_PRIMARY_REF_FRAME_BIT_KHR = new VkVideoEncodeAV1StdFlagBitsKHR(4);
    public static final VkVideoEncodeAV1StdFlagBitsKHR VK_VIDEO_ENCODE_AV1_STD_SKIP_MODE_PRESENT_UNSET_BIT_KHR = new VkVideoEncodeAV1StdFlagBitsKHR(2);
    public static final VkVideoEncodeAV1StdFlagBitsKHR VK_VIDEO_ENCODE_AV1_STD_UNIFORM_TILE_SPACING_FLAG_SET_BIT_KHR = new VkVideoEncodeAV1StdFlagBitsKHR(1);

    public static VkVideoEncodeAV1StdFlagBitsKHR fromValue(int value) {
        return switch (value) {
            case 8 -> VK_VIDEO_ENCODE_AV1_STD_DELTA_Q_BIT_KHR;
            case 2147483647 -> VK_VIDEO_ENCODE_AV1_STD_FLAG_BITS_MAX_ENUM_KHR;
            case 4 -> VK_VIDEO_ENCODE_AV1_STD_PRIMARY_REF_FRAME_BIT_KHR;
            case 2 -> VK_VIDEO_ENCODE_AV1_STD_SKIP_MODE_PRESENT_UNSET_BIT_KHR;
            case 1 -> VK_VIDEO_ENCODE_AV1_STD_UNIFORM_TILE_SPACING_FLAG_SET_BIT_KHR;
            default -> new VkVideoEncodeAV1StdFlagBitsKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
