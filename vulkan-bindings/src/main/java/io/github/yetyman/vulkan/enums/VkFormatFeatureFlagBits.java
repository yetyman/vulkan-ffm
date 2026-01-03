package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkFormatFeatureFlagBits
 * Generated from jextract bindings
 */
public record VkFormatFeatureFlagBits(int value) {

    public static final VkFormatFeatureFlagBits VK_FORMAT_FEATURE_ACCELERATION_STRUCTURE_VERTEX_BUFFER_BIT_KHR = new VkFormatFeatureFlagBits(536870912);
    public static final VkFormatFeatureFlagBits VK_FORMAT_FEATURE_BLIT_DST_BIT = new VkFormatFeatureFlagBits(2048);
    public static final VkFormatFeatureFlagBits VK_FORMAT_FEATURE_BLIT_SRC_BIT = new VkFormatFeatureFlagBits(1024);
    public static final VkFormatFeatureFlagBits VK_FORMAT_FEATURE_COLOR_ATTACHMENT_BIT = new VkFormatFeatureFlagBits(128);
    public static final VkFormatFeatureFlagBits VK_FORMAT_FEATURE_COLOR_ATTACHMENT_BLEND_BIT = new VkFormatFeatureFlagBits(256);
    public static final VkFormatFeatureFlagBits VK_FORMAT_FEATURE_COSITED_CHROMA_SAMPLES_BIT = new VkFormatFeatureFlagBits(8388608);
    public static final VkFormatFeatureFlagBits VK_FORMAT_FEATURE_COSITED_CHROMA_SAMPLES_BIT_KHR = VK_FORMAT_FEATURE_COSITED_CHROMA_SAMPLES_BIT;
    public static final VkFormatFeatureFlagBits VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT = new VkFormatFeatureFlagBits(512);
    public static final VkFormatFeatureFlagBits VK_FORMAT_FEATURE_DISJOINT_BIT = new VkFormatFeatureFlagBits(4194304);
    public static final VkFormatFeatureFlagBits VK_FORMAT_FEATURE_DISJOINT_BIT_KHR = VK_FORMAT_FEATURE_DISJOINT_BIT;
    public static final VkFormatFeatureFlagBits VK_FORMAT_FEATURE_FLAG_BITS_MAX_ENUM = new VkFormatFeatureFlagBits(2147483647);
    public static final VkFormatFeatureFlagBits VK_FORMAT_FEATURE_FRAGMENT_DENSITY_MAP_BIT_EXT = new VkFormatFeatureFlagBits(16777216);
    public static final VkFormatFeatureFlagBits VK_FORMAT_FEATURE_FRAGMENT_SHADING_RATE_ATTACHMENT_BIT_KHR = new VkFormatFeatureFlagBits(1073741824);
    public static final VkFormatFeatureFlagBits VK_FORMAT_FEATURE_MIDPOINT_CHROMA_SAMPLES_BIT = new VkFormatFeatureFlagBits(131072);
    public static final VkFormatFeatureFlagBits VK_FORMAT_FEATURE_MIDPOINT_CHROMA_SAMPLES_BIT_KHR = VK_FORMAT_FEATURE_MIDPOINT_CHROMA_SAMPLES_BIT;
    public static final VkFormatFeatureFlagBits VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT = new VkFormatFeatureFlagBits(1);
    public static final VkFormatFeatureFlagBits VK_FORMAT_FEATURE_SAMPLED_IMAGE_FILTER_CUBIC_BIT_EXT = new VkFormatFeatureFlagBits(8192);
    public static final VkFormatFeatureFlagBits VK_FORMAT_FEATURE_SAMPLED_IMAGE_FILTER_CUBIC_BIT_IMG = VK_FORMAT_FEATURE_SAMPLED_IMAGE_FILTER_CUBIC_BIT_EXT;
    public static final VkFormatFeatureFlagBits VK_FORMAT_FEATURE_SAMPLED_IMAGE_FILTER_LINEAR_BIT = new VkFormatFeatureFlagBits(4096);
    public static final VkFormatFeatureFlagBits VK_FORMAT_FEATURE_SAMPLED_IMAGE_FILTER_MINMAX_BIT = new VkFormatFeatureFlagBits(65536);
    public static final VkFormatFeatureFlagBits VK_FORMAT_FEATURE_SAMPLED_IMAGE_FILTER_MINMAX_BIT_EXT = VK_FORMAT_FEATURE_SAMPLED_IMAGE_FILTER_MINMAX_BIT;
    public static final VkFormatFeatureFlagBits VK_FORMAT_FEATURE_SAMPLED_IMAGE_YCBCR_CONVERSION_CHROMA_RECONSTRUCTION_EXPLICIT_BIT = new VkFormatFeatureFlagBits(1048576);
    public static final VkFormatFeatureFlagBits VK_FORMAT_FEATURE_SAMPLED_IMAGE_YCBCR_CONVERSION_CHROMA_RECONSTRUCTION_EXPLICIT_BIT_KHR = VK_FORMAT_FEATURE_SAMPLED_IMAGE_YCBCR_CONVERSION_CHROMA_RECONSTRUCTION_EXPLICIT_BIT;
    public static final VkFormatFeatureFlagBits VK_FORMAT_FEATURE_SAMPLED_IMAGE_YCBCR_CONVERSION_CHROMA_RECONSTRUCTION_EXPLICIT_FORCEABLE_BIT = new VkFormatFeatureFlagBits(2097152);
    public static final VkFormatFeatureFlagBits VK_FORMAT_FEATURE_SAMPLED_IMAGE_YCBCR_CONVERSION_CHROMA_RECONSTRUCTION_EXPLICIT_FORCEABLE_BIT_KHR = VK_FORMAT_FEATURE_SAMPLED_IMAGE_YCBCR_CONVERSION_CHROMA_RECONSTRUCTION_EXPLICIT_FORCEABLE_BIT;
    public static final VkFormatFeatureFlagBits VK_FORMAT_FEATURE_SAMPLED_IMAGE_YCBCR_CONVERSION_LINEAR_FILTER_BIT = new VkFormatFeatureFlagBits(262144);
    public static final VkFormatFeatureFlagBits VK_FORMAT_FEATURE_SAMPLED_IMAGE_YCBCR_CONVERSION_LINEAR_FILTER_BIT_KHR = VK_FORMAT_FEATURE_SAMPLED_IMAGE_YCBCR_CONVERSION_LINEAR_FILTER_BIT;
    public static final VkFormatFeatureFlagBits VK_FORMAT_FEATURE_SAMPLED_IMAGE_YCBCR_CONVERSION_SEPARATE_RECONSTRUCTION_FILTER_BIT = new VkFormatFeatureFlagBits(524288);
    public static final VkFormatFeatureFlagBits VK_FORMAT_FEATURE_SAMPLED_IMAGE_YCBCR_CONVERSION_SEPARATE_RECONSTRUCTION_FILTER_BIT_KHR = VK_FORMAT_FEATURE_SAMPLED_IMAGE_YCBCR_CONVERSION_SEPARATE_RECONSTRUCTION_FILTER_BIT;
    public static final VkFormatFeatureFlagBits VK_FORMAT_FEATURE_STORAGE_IMAGE_ATOMIC_BIT = new VkFormatFeatureFlagBits(4);
    public static final VkFormatFeatureFlagBits VK_FORMAT_FEATURE_STORAGE_IMAGE_BIT = new VkFormatFeatureFlagBits(2);
    public static final VkFormatFeatureFlagBits VK_FORMAT_FEATURE_STORAGE_TEXEL_BUFFER_ATOMIC_BIT = new VkFormatFeatureFlagBits(32);
    public static final VkFormatFeatureFlagBits VK_FORMAT_FEATURE_STORAGE_TEXEL_BUFFER_BIT = new VkFormatFeatureFlagBits(16);
    public static final VkFormatFeatureFlagBits VK_FORMAT_FEATURE_TRANSFER_DST_BIT = new VkFormatFeatureFlagBits(32768);
    public static final VkFormatFeatureFlagBits VK_FORMAT_FEATURE_TRANSFER_DST_BIT_KHR = VK_FORMAT_FEATURE_TRANSFER_DST_BIT;
    public static final VkFormatFeatureFlagBits VK_FORMAT_FEATURE_TRANSFER_SRC_BIT = new VkFormatFeatureFlagBits(16384);
    public static final VkFormatFeatureFlagBits VK_FORMAT_FEATURE_TRANSFER_SRC_BIT_KHR = VK_FORMAT_FEATURE_TRANSFER_SRC_BIT;
    public static final VkFormatFeatureFlagBits VK_FORMAT_FEATURE_UNIFORM_TEXEL_BUFFER_BIT = new VkFormatFeatureFlagBits(8);
    public static final VkFormatFeatureFlagBits VK_FORMAT_FEATURE_VERTEX_BUFFER_BIT = new VkFormatFeatureFlagBits(64);
    public static final VkFormatFeatureFlagBits VK_FORMAT_FEATURE_VIDEO_DECODE_DPB_BIT_KHR = new VkFormatFeatureFlagBits(67108864);
    public static final VkFormatFeatureFlagBits VK_FORMAT_FEATURE_VIDEO_DECODE_OUTPUT_BIT_KHR = new VkFormatFeatureFlagBits(33554432);
    public static final VkFormatFeatureFlagBits VK_FORMAT_FEATURE_VIDEO_ENCODE_DPB_BIT_KHR = new VkFormatFeatureFlagBits(268435456);
    public static final VkFormatFeatureFlagBits VK_FORMAT_FEATURE_VIDEO_ENCODE_INPUT_BIT_KHR = new VkFormatFeatureFlagBits(134217728);

    public static VkFormatFeatureFlagBits fromValue(int value) {
        return switch (value) {
            case 536870912 -> VK_FORMAT_FEATURE_ACCELERATION_STRUCTURE_VERTEX_BUFFER_BIT_KHR;
            case 2048 -> VK_FORMAT_FEATURE_BLIT_DST_BIT;
            case 1024 -> VK_FORMAT_FEATURE_BLIT_SRC_BIT;
            case 128 -> VK_FORMAT_FEATURE_COLOR_ATTACHMENT_BIT;
            case 256 -> VK_FORMAT_FEATURE_COLOR_ATTACHMENT_BLEND_BIT;
            case 8388608 -> VK_FORMAT_FEATURE_COSITED_CHROMA_SAMPLES_BIT;
            case 512 -> VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT;
            case 4194304 -> VK_FORMAT_FEATURE_DISJOINT_BIT;
            case 2147483647 -> VK_FORMAT_FEATURE_FLAG_BITS_MAX_ENUM;
            case 16777216 -> VK_FORMAT_FEATURE_FRAGMENT_DENSITY_MAP_BIT_EXT;
            case 1073741824 -> VK_FORMAT_FEATURE_FRAGMENT_SHADING_RATE_ATTACHMENT_BIT_KHR;
            case 131072 -> VK_FORMAT_FEATURE_MIDPOINT_CHROMA_SAMPLES_BIT;
            case 1 -> VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT;
            case 8192 -> VK_FORMAT_FEATURE_SAMPLED_IMAGE_FILTER_CUBIC_BIT_IMG;
            case 4096 -> VK_FORMAT_FEATURE_SAMPLED_IMAGE_FILTER_LINEAR_BIT;
            case 65536 -> VK_FORMAT_FEATURE_SAMPLED_IMAGE_FILTER_MINMAX_BIT;
            case 1048576 -> VK_FORMAT_FEATURE_SAMPLED_IMAGE_YCBCR_CONVERSION_CHROMA_RECONSTRUCTION_EXPLICIT_BIT;
            case 2097152 -> VK_FORMAT_FEATURE_SAMPLED_IMAGE_YCBCR_CONVERSION_CHROMA_RECONSTRUCTION_EXPLICIT_FORCEABLE_BIT;
            case 262144 -> VK_FORMAT_FEATURE_SAMPLED_IMAGE_YCBCR_CONVERSION_LINEAR_FILTER_BIT;
            case 524288 -> VK_FORMAT_FEATURE_SAMPLED_IMAGE_YCBCR_CONVERSION_SEPARATE_RECONSTRUCTION_FILTER_BIT;
            case 4 -> VK_FORMAT_FEATURE_STORAGE_IMAGE_ATOMIC_BIT;
            case 2 -> VK_FORMAT_FEATURE_STORAGE_IMAGE_BIT;
            case 32 -> VK_FORMAT_FEATURE_STORAGE_TEXEL_BUFFER_ATOMIC_BIT;
            case 16 -> VK_FORMAT_FEATURE_STORAGE_TEXEL_BUFFER_BIT;
            case 32768 -> VK_FORMAT_FEATURE_TRANSFER_DST_BIT;
            case 16384 -> VK_FORMAT_FEATURE_TRANSFER_SRC_BIT;
            case 8 -> VK_FORMAT_FEATURE_UNIFORM_TEXEL_BUFFER_BIT;
            case 64 -> VK_FORMAT_FEATURE_VERTEX_BUFFER_BIT;
            case 67108864 -> VK_FORMAT_FEATURE_VIDEO_DECODE_DPB_BIT_KHR;
            case 33554432 -> VK_FORMAT_FEATURE_VIDEO_DECODE_OUTPUT_BIT_KHR;
            case 268435456 -> VK_FORMAT_FEATURE_VIDEO_ENCODE_DPB_BIT_KHR;
            case 134217728 -> VK_FORMAT_FEATURE_VIDEO_ENCODE_INPUT_BIT_KHR;
            default -> new VkFormatFeatureFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
