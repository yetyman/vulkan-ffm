package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkVideoEncodeAV1SuperblockSizeFlagBitsKHR
 * Generated from jextract bindings
 */
public record VkVideoEncodeAV1SuperblockSizeFlagBitsKHR(int value) {

    public static final VkVideoEncodeAV1SuperblockSizeFlagBitsKHR VK_VIDEO_ENCODE_AV1_SUPERBLOCK_SIZE_128_BIT_KHR = new VkVideoEncodeAV1SuperblockSizeFlagBitsKHR(2);
    public static final VkVideoEncodeAV1SuperblockSizeFlagBitsKHR VK_VIDEO_ENCODE_AV1_SUPERBLOCK_SIZE_64_BIT_KHR = new VkVideoEncodeAV1SuperblockSizeFlagBitsKHR(1);
    public static final VkVideoEncodeAV1SuperblockSizeFlagBitsKHR VK_VIDEO_ENCODE_AV1_SUPERBLOCK_SIZE_FLAG_BITS_MAX_ENUM_KHR = new VkVideoEncodeAV1SuperblockSizeFlagBitsKHR(2147483647);

    public static VkVideoEncodeAV1SuperblockSizeFlagBitsKHR fromValue(int value) {
        return switch (value) {
            case 2 -> VK_VIDEO_ENCODE_AV1_SUPERBLOCK_SIZE_128_BIT_KHR;
            case 1 -> VK_VIDEO_ENCODE_AV1_SUPERBLOCK_SIZE_64_BIT_KHR;
            case 2147483647 -> VK_VIDEO_ENCODE_AV1_SUPERBLOCK_SIZE_FLAG_BITS_MAX_ENUM_KHR;
            default -> new VkVideoEncodeAV1SuperblockSizeFlagBitsKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
