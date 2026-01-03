package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkVideoEncodeIntraRefreshModeFlagBitsKHR
 * Generated from jextract bindings
 */
public record VkVideoEncodeIntraRefreshModeFlagBitsKHR(int value) {

    public static final VkVideoEncodeIntraRefreshModeFlagBitsKHR VK_VIDEO_ENCODE_INTRA_REFRESH_MODE_BLOCK_BASED_BIT_KHR = new VkVideoEncodeIntraRefreshModeFlagBitsKHR(2);
    public static final VkVideoEncodeIntraRefreshModeFlagBitsKHR VK_VIDEO_ENCODE_INTRA_REFRESH_MODE_BLOCK_COLUMN_BASED_BIT_KHR = new VkVideoEncodeIntraRefreshModeFlagBitsKHR(8);
    public static final VkVideoEncodeIntraRefreshModeFlagBitsKHR VK_VIDEO_ENCODE_INTRA_REFRESH_MODE_BLOCK_ROW_BASED_BIT_KHR = new VkVideoEncodeIntraRefreshModeFlagBitsKHR(4);
    public static final VkVideoEncodeIntraRefreshModeFlagBitsKHR VK_VIDEO_ENCODE_INTRA_REFRESH_MODE_FLAG_BITS_MAX_ENUM_KHR = new VkVideoEncodeIntraRefreshModeFlagBitsKHR(2147483647);
    public static final VkVideoEncodeIntraRefreshModeFlagBitsKHR VK_VIDEO_ENCODE_INTRA_REFRESH_MODE_NONE_KHR = new VkVideoEncodeIntraRefreshModeFlagBitsKHR(0);
    public static final VkVideoEncodeIntraRefreshModeFlagBitsKHR VK_VIDEO_ENCODE_INTRA_REFRESH_MODE_PER_PICTURE_PARTITION_BIT_KHR = new VkVideoEncodeIntraRefreshModeFlagBitsKHR(1);

    public static VkVideoEncodeIntraRefreshModeFlagBitsKHR fromValue(int value) {
        return switch (value) {
            case 2 -> VK_VIDEO_ENCODE_INTRA_REFRESH_MODE_BLOCK_BASED_BIT_KHR;
            case 8 -> VK_VIDEO_ENCODE_INTRA_REFRESH_MODE_BLOCK_COLUMN_BASED_BIT_KHR;
            case 4 -> VK_VIDEO_ENCODE_INTRA_REFRESH_MODE_BLOCK_ROW_BASED_BIT_KHR;
            case 2147483647 -> VK_VIDEO_ENCODE_INTRA_REFRESH_MODE_FLAG_BITS_MAX_ENUM_KHR;
            case 0 -> VK_VIDEO_ENCODE_INTRA_REFRESH_MODE_NONE_KHR;
            case 1 -> VK_VIDEO_ENCODE_INTRA_REFRESH_MODE_PER_PICTURE_PARTITION_BIT_KHR;
            default -> new VkVideoEncodeIntraRefreshModeFlagBitsKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
