package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkImageCompressionFixedRateFlagBitsEXT
 * Generated from jextract bindings
 */
public record VkImageCompressionFixedRateFlagBitsEXT(int value) {

    public static final VkImageCompressionFixedRateFlagBitsEXT VK_IMAGE_COMPRESSION_FIXED_RATE_10BPC_BIT_EXT = new VkImageCompressionFixedRateFlagBitsEXT(512);
    public static final VkImageCompressionFixedRateFlagBitsEXT VK_IMAGE_COMPRESSION_FIXED_RATE_11BPC_BIT_EXT = new VkImageCompressionFixedRateFlagBitsEXT(1024);
    public static final VkImageCompressionFixedRateFlagBitsEXT VK_IMAGE_COMPRESSION_FIXED_RATE_12BPC_BIT_EXT = new VkImageCompressionFixedRateFlagBitsEXT(2048);
    public static final VkImageCompressionFixedRateFlagBitsEXT VK_IMAGE_COMPRESSION_FIXED_RATE_13BPC_BIT_EXT = new VkImageCompressionFixedRateFlagBitsEXT(4096);
    public static final VkImageCompressionFixedRateFlagBitsEXT VK_IMAGE_COMPRESSION_FIXED_RATE_14BPC_BIT_EXT = new VkImageCompressionFixedRateFlagBitsEXT(8192);
    public static final VkImageCompressionFixedRateFlagBitsEXT VK_IMAGE_COMPRESSION_FIXED_RATE_15BPC_BIT_EXT = new VkImageCompressionFixedRateFlagBitsEXT(16384);
    public static final VkImageCompressionFixedRateFlagBitsEXT VK_IMAGE_COMPRESSION_FIXED_RATE_16BPC_BIT_EXT = new VkImageCompressionFixedRateFlagBitsEXT(32768);
    public static final VkImageCompressionFixedRateFlagBitsEXT VK_IMAGE_COMPRESSION_FIXED_RATE_17BPC_BIT_EXT = new VkImageCompressionFixedRateFlagBitsEXT(65536);
    public static final VkImageCompressionFixedRateFlagBitsEXT VK_IMAGE_COMPRESSION_FIXED_RATE_18BPC_BIT_EXT = new VkImageCompressionFixedRateFlagBitsEXT(131072);
    public static final VkImageCompressionFixedRateFlagBitsEXT VK_IMAGE_COMPRESSION_FIXED_RATE_19BPC_BIT_EXT = new VkImageCompressionFixedRateFlagBitsEXT(262144);
    public static final VkImageCompressionFixedRateFlagBitsEXT VK_IMAGE_COMPRESSION_FIXED_RATE_1BPC_BIT_EXT = new VkImageCompressionFixedRateFlagBitsEXT(1);
    public static final VkImageCompressionFixedRateFlagBitsEXT VK_IMAGE_COMPRESSION_FIXED_RATE_20BPC_BIT_EXT = new VkImageCompressionFixedRateFlagBitsEXT(524288);
    public static final VkImageCompressionFixedRateFlagBitsEXT VK_IMAGE_COMPRESSION_FIXED_RATE_21BPC_BIT_EXT = new VkImageCompressionFixedRateFlagBitsEXT(1048576);
    public static final VkImageCompressionFixedRateFlagBitsEXT VK_IMAGE_COMPRESSION_FIXED_RATE_22BPC_BIT_EXT = new VkImageCompressionFixedRateFlagBitsEXT(2097152);
    public static final VkImageCompressionFixedRateFlagBitsEXT VK_IMAGE_COMPRESSION_FIXED_RATE_23BPC_BIT_EXT = new VkImageCompressionFixedRateFlagBitsEXT(4194304);
    public static final VkImageCompressionFixedRateFlagBitsEXT VK_IMAGE_COMPRESSION_FIXED_RATE_24BPC_BIT_EXT = new VkImageCompressionFixedRateFlagBitsEXT(8388608);
    public static final VkImageCompressionFixedRateFlagBitsEXT VK_IMAGE_COMPRESSION_FIXED_RATE_2BPC_BIT_EXT = new VkImageCompressionFixedRateFlagBitsEXT(2);
    public static final VkImageCompressionFixedRateFlagBitsEXT VK_IMAGE_COMPRESSION_FIXED_RATE_3BPC_BIT_EXT = new VkImageCompressionFixedRateFlagBitsEXT(4);
    public static final VkImageCompressionFixedRateFlagBitsEXT VK_IMAGE_COMPRESSION_FIXED_RATE_4BPC_BIT_EXT = new VkImageCompressionFixedRateFlagBitsEXT(8);
    public static final VkImageCompressionFixedRateFlagBitsEXT VK_IMAGE_COMPRESSION_FIXED_RATE_5BPC_BIT_EXT = new VkImageCompressionFixedRateFlagBitsEXT(16);
    public static final VkImageCompressionFixedRateFlagBitsEXT VK_IMAGE_COMPRESSION_FIXED_RATE_6BPC_BIT_EXT = new VkImageCompressionFixedRateFlagBitsEXT(32);
    public static final VkImageCompressionFixedRateFlagBitsEXT VK_IMAGE_COMPRESSION_FIXED_RATE_7BPC_BIT_EXT = new VkImageCompressionFixedRateFlagBitsEXT(64);
    public static final VkImageCompressionFixedRateFlagBitsEXT VK_IMAGE_COMPRESSION_FIXED_RATE_8BPC_BIT_EXT = new VkImageCompressionFixedRateFlagBitsEXT(128);
    public static final VkImageCompressionFixedRateFlagBitsEXT VK_IMAGE_COMPRESSION_FIXED_RATE_9BPC_BIT_EXT = new VkImageCompressionFixedRateFlagBitsEXT(256);
    public static final VkImageCompressionFixedRateFlagBitsEXT VK_IMAGE_COMPRESSION_FIXED_RATE_FLAG_BITS_MAX_ENUM_EXT = new VkImageCompressionFixedRateFlagBitsEXT(2147483647);
    public static final VkImageCompressionFixedRateFlagBitsEXT VK_IMAGE_COMPRESSION_FIXED_RATE_NONE_EXT = new VkImageCompressionFixedRateFlagBitsEXT(0);

    public static VkImageCompressionFixedRateFlagBitsEXT fromValue(int value) {
        return switch (value) {
            case 512 -> VK_IMAGE_COMPRESSION_FIXED_RATE_10BPC_BIT_EXT;
            case 1024 -> VK_IMAGE_COMPRESSION_FIXED_RATE_11BPC_BIT_EXT;
            case 2048 -> VK_IMAGE_COMPRESSION_FIXED_RATE_12BPC_BIT_EXT;
            case 4096 -> VK_IMAGE_COMPRESSION_FIXED_RATE_13BPC_BIT_EXT;
            case 8192 -> VK_IMAGE_COMPRESSION_FIXED_RATE_14BPC_BIT_EXT;
            case 16384 -> VK_IMAGE_COMPRESSION_FIXED_RATE_15BPC_BIT_EXT;
            case 32768 -> VK_IMAGE_COMPRESSION_FIXED_RATE_16BPC_BIT_EXT;
            case 65536 -> VK_IMAGE_COMPRESSION_FIXED_RATE_17BPC_BIT_EXT;
            case 131072 -> VK_IMAGE_COMPRESSION_FIXED_RATE_18BPC_BIT_EXT;
            case 262144 -> VK_IMAGE_COMPRESSION_FIXED_RATE_19BPC_BIT_EXT;
            case 1 -> VK_IMAGE_COMPRESSION_FIXED_RATE_1BPC_BIT_EXT;
            case 524288 -> VK_IMAGE_COMPRESSION_FIXED_RATE_20BPC_BIT_EXT;
            case 1048576 -> VK_IMAGE_COMPRESSION_FIXED_RATE_21BPC_BIT_EXT;
            case 2097152 -> VK_IMAGE_COMPRESSION_FIXED_RATE_22BPC_BIT_EXT;
            case 4194304 -> VK_IMAGE_COMPRESSION_FIXED_RATE_23BPC_BIT_EXT;
            case 8388608 -> VK_IMAGE_COMPRESSION_FIXED_RATE_24BPC_BIT_EXT;
            case 2 -> VK_IMAGE_COMPRESSION_FIXED_RATE_2BPC_BIT_EXT;
            case 4 -> VK_IMAGE_COMPRESSION_FIXED_RATE_3BPC_BIT_EXT;
            case 8 -> VK_IMAGE_COMPRESSION_FIXED_RATE_4BPC_BIT_EXT;
            case 16 -> VK_IMAGE_COMPRESSION_FIXED_RATE_5BPC_BIT_EXT;
            case 32 -> VK_IMAGE_COMPRESSION_FIXED_RATE_6BPC_BIT_EXT;
            case 64 -> VK_IMAGE_COMPRESSION_FIXED_RATE_7BPC_BIT_EXT;
            case 128 -> VK_IMAGE_COMPRESSION_FIXED_RATE_8BPC_BIT_EXT;
            case 256 -> VK_IMAGE_COMPRESSION_FIXED_RATE_9BPC_BIT_EXT;
            case 2147483647 -> VK_IMAGE_COMPRESSION_FIXED_RATE_FLAG_BITS_MAX_ENUM_EXT;
            case 0 -> VK_IMAGE_COMPRESSION_FIXED_RATE_NONE_EXT;
            default -> new VkImageCompressionFixedRateFlagBitsEXT(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
