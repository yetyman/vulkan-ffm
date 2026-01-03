package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkVideoEncodeContentFlagBitsKHR
 * Generated from jextract bindings
 */
public record VkVideoEncodeContentFlagBitsKHR(int value) {

    public static final VkVideoEncodeContentFlagBitsKHR VK_VIDEO_ENCODE_CONTENT_CAMERA_BIT_KHR = new VkVideoEncodeContentFlagBitsKHR(1);
    public static final VkVideoEncodeContentFlagBitsKHR VK_VIDEO_ENCODE_CONTENT_DEFAULT_KHR = new VkVideoEncodeContentFlagBitsKHR(0);
    public static final VkVideoEncodeContentFlagBitsKHR VK_VIDEO_ENCODE_CONTENT_DESKTOP_BIT_KHR = new VkVideoEncodeContentFlagBitsKHR(2);
    public static final VkVideoEncodeContentFlagBitsKHR VK_VIDEO_ENCODE_CONTENT_FLAG_BITS_MAX_ENUM_KHR = new VkVideoEncodeContentFlagBitsKHR(2147483647);
    public static final VkVideoEncodeContentFlagBitsKHR VK_VIDEO_ENCODE_CONTENT_RENDERED_BIT_KHR = new VkVideoEncodeContentFlagBitsKHR(4);

    public static VkVideoEncodeContentFlagBitsKHR fromValue(int value) {
        return switch (value) {
            case 1 -> VK_VIDEO_ENCODE_CONTENT_CAMERA_BIT_KHR;
            case 0 -> VK_VIDEO_ENCODE_CONTENT_DEFAULT_KHR;
            case 2 -> VK_VIDEO_ENCODE_CONTENT_DESKTOP_BIT_KHR;
            case 2147483647 -> VK_VIDEO_ENCODE_CONTENT_FLAG_BITS_MAX_ENUM_KHR;
            case 4 -> VK_VIDEO_ENCODE_CONTENT_RENDERED_BIT_KHR;
            default -> new VkVideoEncodeContentFlagBitsKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
