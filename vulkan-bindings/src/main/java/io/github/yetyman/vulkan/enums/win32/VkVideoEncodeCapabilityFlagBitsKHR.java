package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkVideoEncodeCapabilityFlagBitsKHR
 * Generated from jextract bindings
 */
public record VkVideoEncodeCapabilityFlagBitsKHR(int value) {

    public static final VkVideoEncodeCapabilityFlagBitsKHR VK_VIDEO_ENCODE_CAPABILITY_EMPHASIS_MAP_BIT_KHR = new VkVideoEncodeCapabilityFlagBitsKHR(8);
    public static final VkVideoEncodeCapabilityFlagBitsKHR VK_VIDEO_ENCODE_CAPABILITY_FLAG_BITS_MAX_ENUM_KHR = new VkVideoEncodeCapabilityFlagBitsKHR(2147483647);
    public static final VkVideoEncodeCapabilityFlagBitsKHR VK_VIDEO_ENCODE_CAPABILITY_INSUFFICIENT_BITSTREAM_BUFFER_RANGE_DETECTION_BIT_KHR = new VkVideoEncodeCapabilityFlagBitsKHR(2);
    public static final VkVideoEncodeCapabilityFlagBitsKHR VK_VIDEO_ENCODE_CAPABILITY_PRECEDING_EXTERNALLY_ENCODED_BYTES_BIT_KHR = new VkVideoEncodeCapabilityFlagBitsKHR(1);
    public static final VkVideoEncodeCapabilityFlagBitsKHR VK_VIDEO_ENCODE_CAPABILITY_QUANTIZATION_DELTA_MAP_BIT_KHR = new VkVideoEncodeCapabilityFlagBitsKHR(4);

    public static VkVideoEncodeCapabilityFlagBitsKHR fromValue(int value) {
        return switch (value) {
            case 8 -> VK_VIDEO_ENCODE_CAPABILITY_EMPHASIS_MAP_BIT_KHR;
            case 2147483647 -> VK_VIDEO_ENCODE_CAPABILITY_FLAG_BITS_MAX_ENUM_KHR;
            case 2 -> VK_VIDEO_ENCODE_CAPABILITY_INSUFFICIENT_BITSTREAM_BUFFER_RANGE_DETECTION_BIT_KHR;
            case 1 -> VK_VIDEO_ENCODE_CAPABILITY_PRECEDING_EXTERNALLY_ENCODED_BYTES_BIT_KHR;
            case 4 -> VK_VIDEO_ENCODE_CAPABILITY_QUANTIZATION_DELTA_MAP_BIT_KHR;
            default -> new VkVideoEncodeCapabilityFlagBitsKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
