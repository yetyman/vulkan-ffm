package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkVideoDecodeCapabilityFlagBitsKHR
 * Generated from jextract bindings
 */
public record VkVideoDecodeCapabilityFlagBitsKHR(int value) {

    public static final VkVideoDecodeCapabilityFlagBitsKHR VK_VIDEO_DECODE_CAPABILITY_DPB_AND_OUTPUT_COINCIDE_BIT_KHR = new VkVideoDecodeCapabilityFlagBitsKHR(1);
    public static final VkVideoDecodeCapabilityFlagBitsKHR VK_VIDEO_DECODE_CAPABILITY_DPB_AND_OUTPUT_DISTINCT_BIT_KHR = new VkVideoDecodeCapabilityFlagBitsKHR(2);
    public static final VkVideoDecodeCapabilityFlagBitsKHR VK_VIDEO_DECODE_CAPABILITY_FLAG_BITS_MAX_ENUM_KHR = new VkVideoDecodeCapabilityFlagBitsKHR(2147483647);

    public static VkVideoDecodeCapabilityFlagBitsKHR fromValue(int value) {
        return switch (value) {
            case 1 -> VK_VIDEO_DECODE_CAPABILITY_DPB_AND_OUTPUT_COINCIDE_BIT_KHR;
            case 2 -> VK_VIDEO_DECODE_CAPABILITY_DPB_AND_OUTPUT_DISTINCT_BIT_KHR;
            case 2147483647 -> VK_VIDEO_DECODE_CAPABILITY_FLAG_BITS_MAX_ENUM_KHR;
            default -> new VkVideoDecodeCapabilityFlagBitsKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
