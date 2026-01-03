package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkImageUsageFlagBits
 * Generated from jextract bindings
 */
public record VkImageUsageFlagBits(int value) {

    public static final VkImageUsageFlagBits VK_IMAGE_USAGE_ATTACHMENT_FEEDBACK_LOOP_BIT_EXT = new VkImageUsageFlagBits(524288);
    public static final VkImageUsageFlagBits VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT = new VkImageUsageFlagBits(16);
    public static final VkImageUsageFlagBits VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT = new VkImageUsageFlagBits(32);
    public static final VkImageUsageFlagBits VK_IMAGE_USAGE_FLAG_BITS_MAX_ENUM = new VkImageUsageFlagBits(2147483647);
    public static final VkImageUsageFlagBits VK_IMAGE_USAGE_FRAGMENT_DENSITY_MAP_BIT_EXT = new VkImageUsageFlagBits(512);
    public static final VkImageUsageFlagBits VK_IMAGE_USAGE_FRAGMENT_SHADING_RATE_ATTACHMENT_BIT_KHR = new VkImageUsageFlagBits(256);
    public static final VkImageUsageFlagBits VK_IMAGE_USAGE_HOST_TRANSFER_BIT = new VkImageUsageFlagBits(4194304);
    public static final VkImageUsageFlagBits VK_IMAGE_USAGE_HOST_TRANSFER_BIT_EXT = new VkImageUsageFlagBits(4194304);
    public static final VkImageUsageFlagBits VK_IMAGE_USAGE_INPUT_ATTACHMENT_BIT = new VkImageUsageFlagBits(128);
    public static final VkImageUsageFlagBits VK_IMAGE_USAGE_INVOCATION_MASK_BIT_HUAWEI = new VkImageUsageFlagBits(262144);
    public static final VkImageUsageFlagBits VK_IMAGE_USAGE_SAMPLED_BIT = new VkImageUsageFlagBits(4);
    public static final VkImageUsageFlagBits VK_IMAGE_USAGE_SAMPLE_BLOCK_MATCH_BIT_QCOM = new VkImageUsageFlagBits(2097152);
    public static final VkImageUsageFlagBits VK_IMAGE_USAGE_SAMPLE_WEIGHT_BIT_QCOM = new VkImageUsageFlagBits(1048576);
    public static final VkImageUsageFlagBits VK_IMAGE_USAGE_SHADING_RATE_IMAGE_BIT_NV = new VkImageUsageFlagBits(256);
    public static final VkImageUsageFlagBits VK_IMAGE_USAGE_STORAGE_BIT = new VkImageUsageFlagBits(8);
    public static final VkImageUsageFlagBits VK_IMAGE_USAGE_TENSOR_ALIASING_BIT_ARM = new VkImageUsageFlagBits(8388608);
    public static final VkImageUsageFlagBits VK_IMAGE_USAGE_TILE_MEMORY_BIT_QCOM = new VkImageUsageFlagBits(134217728);
    public static final VkImageUsageFlagBits VK_IMAGE_USAGE_TRANSFER_DST_BIT = new VkImageUsageFlagBits(2);
    public static final VkImageUsageFlagBits VK_IMAGE_USAGE_TRANSFER_SRC_BIT = new VkImageUsageFlagBits(1);
    public static final VkImageUsageFlagBits VK_IMAGE_USAGE_TRANSIENT_ATTACHMENT_BIT = new VkImageUsageFlagBits(64);
    public static final VkImageUsageFlagBits VK_IMAGE_USAGE_VIDEO_DECODE_DPB_BIT_KHR = new VkImageUsageFlagBits(4096);
    public static final VkImageUsageFlagBits VK_IMAGE_USAGE_VIDEO_DECODE_DST_BIT_KHR = new VkImageUsageFlagBits(1024);
    public static final VkImageUsageFlagBits VK_IMAGE_USAGE_VIDEO_DECODE_SRC_BIT_KHR = new VkImageUsageFlagBits(2048);
    public static final VkImageUsageFlagBits VK_IMAGE_USAGE_VIDEO_ENCODE_DPB_BIT_KHR = new VkImageUsageFlagBits(32768);
    public static final VkImageUsageFlagBits VK_IMAGE_USAGE_VIDEO_ENCODE_DST_BIT_KHR = new VkImageUsageFlagBits(8192);
    public static final VkImageUsageFlagBits VK_IMAGE_USAGE_VIDEO_ENCODE_EMPHASIS_MAP_BIT_KHR = new VkImageUsageFlagBits(67108864);
    public static final VkImageUsageFlagBits VK_IMAGE_USAGE_VIDEO_ENCODE_QUANTIZATION_DELTA_MAP_BIT_KHR = new VkImageUsageFlagBits(33554432);
    public static final VkImageUsageFlagBits VK_IMAGE_USAGE_VIDEO_ENCODE_SRC_BIT_KHR = new VkImageUsageFlagBits(16384);

    public static VkImageUsageFlagBits fromValue(int value) {
        return switch (value) {
            case 524288 -> VK_IMAGE_USAGE_ATTACHMENT_FEEDBACK_LOOP_BIT_EXT;
            case 16 -> VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
            case 32 -> VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT;
            case 2147483647 -> VK_IMAGE_USAGE_FLAG_BITS_MAX_ENUM;
            case 512 -> VK_IMAGE_USAGE_FRAGMENT_DENSITY_MAP_BIT_EXT;
            case 256 -> VK_IMAGE_USAGE_SHADING_RATE_IMAGE_BIT_NV;
            case 4194304 -> VK_IMAGE_USAGE_HOST_TRANSFER_BIT;
            case 128 -> VK_IMAGE_USAGE_INPUT_ATTACHMENT_BIT;
            case 262144 -> VK_IMAGE_USAGE_INVOCATION_MASK_BIT_HUAWEI;
            case 4 -> VK_IMAGE_USAGE_SAMPLED_BIT;
            case 2097152 -> VK_IMAGE_USAGE_SAMPLE_BLOCK_MATCH_BIT_QCOM;
            case 1048576 -> VK_IMAGE_USAGE_SAMPLE_WEIGHT_BIT_QCOM;
            case 8 -> VK_IMAGE_USAGE_STORAGE_BIT;
            case 8388608 -> VK_IMAGE_USAGE_TENSOR_ALIASING_BIT_ARM;
            case 134217728 -> VK_IMAGE_USAGE_TILE_MEMORY_BIT_QCOM;
            case 2 -> VK_IMAGE_USAGE_TRANSFER_DST_BIT;
            case 1 -> VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
            case 64 -> VK_IMAGE_USAGE_TRANSIENT_ATTACHMENT_BIT;
            case 4096 -> VK_IMAGE_USAGE_VIDEO_DECODE_DPB_BIT_KHR;
            case 1024 -> VK_IMAGE_USAGE_VIDEO_DECODE_DST_BIT_KHR;
            case 2048 -> VK_IMAGE_USAGE_VIDEO_DECODE_SRC_BIT_KHR;
            case 32768 -> VK_IMAGE_USAGE_VIDEO_ENCODE_DPB_BIT_KHR;
            case 8192 -> VK_IMAGE_USAGE_VIDEO_ENCODE_DST_BIT_KHR;
            case 67108864 -> VK_IMAGE_USAGE_VIDEO_ENCODE_EMPHASIS_MAP_BIT_KHR;
            case 33554432 -> VK_IMAGE_USAGE_VIDEO_ENCODE_QUANTIZATION_DELTA_MAP_BIT_KHR;
            case 16384 -> VK_IMAGE_USAGE_VIDEO_ENCODE_SRC_BIT_KHR;
            default -> new VkImageUsageFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
