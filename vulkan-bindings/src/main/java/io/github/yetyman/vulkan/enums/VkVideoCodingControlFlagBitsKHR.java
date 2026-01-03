package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkVideoCodingControlFlagBitsKHR
 * Generated from jextract bindings
 */
public record VkVideoCodingControlFlagBitsKHR(int value) {

    public static final VkVideoCodingControlFlagBitsKHR VK_VIDEO_CODING_CONTROL_ENCODE_QUALITY_LEVEL_BIT_KHR = new VkVideoCodingControlFlagBitsKHR(4);
    public static final VkVideoCodingControlFlagBitsKHR VK_VIDEO_CODING_CONTROL_ENCODE_RATE_CONTROL_BIT_KHR = new VkVideoCodingControlFlagBitsKHR(2);
    public static final VkVideoCodingControlFlagBitsKHR VK_VIDEO_CODING_CONTROL_FLAG_BITS_MAX_ENUM_KHR = new VkVideoCodingControlFlagBitsKHR(2147483647);
    public static final VkVideoCodingControlFlagBitsKHR VK_VIDEO_CODING_CONTROL_RESET_BIT_KHR = new VkVideoCodingControlFlagBitsKHR(1);

    public static VkVideoCodingControlFlagBitsKHR fromValue(int value) {
        return switch (value) {
            case 4 -> VK_VIDEO_CODING_CONTROL_ENCODE_QUALITY_LEVEL_BIT_KHR;
            case 2 -> VK_VIDEO_CODING_CONTROL_ENCODE_RATE_CONTROL_BIT_KHR;
            case 2147483647 -> VK_VIDEO_CODING_CONTROL_FLAG_BITS_MAX_ENUM_KHR;
            case 1 -> VK_VIDEO_CODING_CONTROL_RESET_BIT_KHR;
            default -> new VkVideoCodingControlFlagBitsKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
