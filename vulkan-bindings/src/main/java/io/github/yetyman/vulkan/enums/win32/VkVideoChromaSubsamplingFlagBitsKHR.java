package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkVideoChromaSubsamplingFlagBitsKHR
 * Generated from jextract bindings
 */
public record VkVideoChromaSubsamplingFlagBitsKHR(int value) {

    public static final VkVideoChromaSubsamplingFlagBitsKHR VK_VIDEO_CHROMA_SUBSAMPLING_420_BIT_KHR = new VkVideoChromaSubsamplingFlagBitsKHR(2);
    public static final VkVideoChromaSubsamplingFlagBitsKHR VK_VIDEO_CHROMA_SUBSAMPLING_422_BIT_KHR = new VkVideoChromaSubsamplingFlagBitsKHR(4);
    public static final VkVideoChromaSubsamplingFlagBitsKHR VK_VIDEO_CHROMA_SUBSAMPLING_444_BIT_KHR = new VkVideoChromaSubsamplingFlagBitsKHR(8);
    public static final VkVideoChromaSubsamplingFlagBitsKHR VK_VIDEO_CHROMA_SUBSAMPLING_FLAG_BITS_MAX_ENUM_KHR = new VkVideoChromaSubsamplingFlagBitsKHR(2147483647);
    public static final VkVideoChromaSubsamplingFlagBitsKHR VK_VIDEO_CHROMA_SUBSAMPLING_INVALID_KHR = new VkVideoChromaSubsamplingFlagBitsKHR(0);
    public static final VkVideoChromaSubsamplingFlagBitsKHR VK_VIDEO_CHROMA_SUBSAMPLING_MONOCHROME_BIT_KHR = new VkVideoChromaSubsamplingFlagBitsKHR(1);

    public static VkVideoChromaSubsamplingFlagBitsKHR fromValue(int value) {
        return switch (value) {
            case 2 -> VK_VIDEO_CHROMA_SUBSAMPLING_420_BIT_KHR;
            case 4 -> VK_VIDEO_CHROMA_SUBSAMPLING_422_BIT_KHR;
            case 8 -> VK_VIDEO_CHROMA_SUBSAMPLING_444_BIT_KHR;
            case 2147483647 -> VK_VIDEO_CHROMA_SUBSAMPLING_FLAG_BITS_MAX_ENUM_KHR;
            case 0 -> VK_VIDEO_CHROMA_SUBSAMPLING_INVALID_KHR;
            case 1 -> VK_VIDEO_CHROMA_SUBSAMPLING_MONOCHROME_BIT_KHR;
            default -> new VkVideoChromaSubsamplingFlagBitsKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
