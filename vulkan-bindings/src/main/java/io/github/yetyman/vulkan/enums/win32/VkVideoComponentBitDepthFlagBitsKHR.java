package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkVideoComponentBitDepthFlagBitsKHR
 * Generated from jextract bindings
 */
public record VkVideoComponentBitDepthFlagBitsKHR(int value) {

    public static final VkVideoComponentBitDepthFlagBitsKHR VK_VIDEO_COMPONENT_BIT_DEPTH_10_BIT_KHR = new VkVideoComponentBitDepthFlagBitsKHR(4);
    public static final VkVideoComponentBitDepthFlagBitsKHR VK_VIDEO_COMPONENT_BIT_DEPTH_12_BIT_KHR = new VkVideoComponentBitDepthFlagBitsKHR(16);
    public static final VkVideoComponentBitDepthFlagBitsKHR VK_VIDEO_COMPONENT_BIT_DEPTH_8_BIT_KHR = new VkVideoComponentBitDepthFlagBitsKHR(1);
    public static final VkVideoComponentBitDepthFlagBitsKHR VK_VIDEO_COMPONENT_BIT_DEPTH_FLAG_BITS_MAX_ENUM_KHR = new VkVideoComponentBitDepthFlagBitsKHR(2147483647);
    public static final VkVideoComponentBitDepthFlagBitsKHR VK_VIDEO_COMPONENT_BIT_DEPTH_INVALID_KHR = new VkVideoComponentBitDepthFlagBitsKHR(0);

    public static VkVideoComponentBitDepthFlagBitsKHR fromValue(int value) {
        return switch (value) {
            case 4 -> VK_VIDEO_COMPONENT_BIT_DEPTH_10_BIT_KHR;
            case 16 -> VK_VIDEO_COMPONENT_BIT_DEPTH_12_BIT_KHR;
            case 1 -> VK_VIDEO_COMPONENT_BIT_DEPTH_8_BIT_KHR;
            case 2147483647 -> VK_VIDEO_COMPONENT_BIT_DEPTH_FLAG_BITS_MAX_ENUM_KHR;
            case 0 -> VK_VIDEO_COMPONENT_BIT_DEPTH_INVALID_KHR;
            default -> new VkVideoComponentBitDepthFlagBitsKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
