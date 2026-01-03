package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkSparseImageFormatFlagBits
 * Generated from jextract bindings
 */
public record VkSparseImageFormatFlagBits(int value) {

    public static final VkSparseImageFormatFlagBits VK_SPARSE_IMAGE_FORMAT_ALIGNED_MIP_SIZE_BIT = new VkSparseImageFormatFlagBits(2);
    public static final VkSparseImageFormatFlagBits VK_SPARSE_IMAGE_FORMAT_FLAG_BITS_MAX_ENUM = new VkSparseImageFormatFlagBits(2147483647);
    public static final VkSparseImageFormatFlagBits VK_SPARSE_IMAGE_FORMAT_NONSTANDARD_BLOCK_SIZE_BIT = new VkSparseImageFormatFlagBits(4);
    public static final VkSparseImageFormatFlagBits VK_SPARSE_IMAGE_FORMAT_SINGLE_MIPTAIL_BIT = new VkSparseImageFormatFlagBits(1);

    public static VkSparseImageFormatFlagBits fromValue(int value) {
        return switch (value) {
            case 2 -> VK_SPARSE_IMAGE_FORMAT_ALIGNED_MIP_SIZE_BIT;
            case 2147483647 -> VK_SPARSE_IMAGE_FORMAT_FLAG_BITS_MAX_ENUM;
            case 4 -> VK_SPARSE_IMAGE_FORMAT_NONSTANDARD_BLOCK_SIZE_BIT;
            case 1 -> VK_SPARSE_IMAGE_FORMAT_SINGLE_MIPTAIL_BIT;
            default -> new VkSparseImageFormatFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
