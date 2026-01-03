package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkSampleCountFlagBits
 * Generated from jextract bindings
 */
public record VkSampleCountFlagBits(int value) {

    public static final VkSampleCountFlagBits VK_SAMPLE_COUNT_16_BIT = new VkSampleCountFlagBits(16);
    public static final VkSampleCountFlagBits VK_SAMPLE_COUNT_1_BIT = new VkSampleCountFlagBits(1);
    public static final VkSampleCountFlagBits VK_SAMPLE_COUNT_2_BIT = new VkSampleCountFlagBits(2);
    public static final VkSampleCountFlagBits VK_SAMPLE_COUNT_32_BIT = new VkSampleCountFlagBits(32);
    public static final VkSampleCountFlagBits VK_SAMPLE_COUNT_4_BIT = new VkSampleCountFlagBits(4);
    public static final VkSampleCountFlagBits VK_SAMPLE_COUNT_64_BIT = new VkSampleCountFlagBits(64);
    public static final VkSampleCountFlagBits VK_SAMPLE_COUNT_8_BIT = new VkSampleCountFlagBits(8);
    public static final VkSampleCountFlagBits VK_SAMPLE_COUNT_FLAG_BITS_MAX_ENUM = new VkSampleCountFlagBits(2147483647);

    public static VkSampleCountFlagBits fromValue(int value) {
        return switch (value) {
            case 16 -> VK_SAMPLE_COUNT_16_BIT;
            case 1 -> VK_SAMPLE_COUNT_1_BIT;
            case 2 -> VK_SAMPLE_COUNT_2_BIT;
            case 32 -> VK_SAMPLE_COUNT_32_BIT;
            case 4 -> VK_SAMPLE_COUNT_4_BIT;
            case 64 -> VK_SAMPLE_COUNT_64_BIT;
            case 8 -> VK_SAMPLE_COUNT_8_BIT;
            case 2147483647 -> VK_SAMPLE_COUNT_FLAG_BITS_MAX_ENUM;
            default -> new VkSampleCountFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
