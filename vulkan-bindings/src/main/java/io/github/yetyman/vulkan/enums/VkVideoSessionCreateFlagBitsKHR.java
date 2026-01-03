package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkVideoSessionCreateFlagBitsKHR
 * Generated from jextract bindings
 */
public record VkVideoSessionCreateFlagBitsKHR(int value) {

    public static final VkVideoSessionCreateFlagBitsKHR VK_VIDEO_SESSION_CREATE_ALLOW_ENCODE_EMPHASIS_MAP_BIT_KHR = new VkVideoSessionCreateFlagBitsKHR(16);
    public static final VkVideoSessionCreateFlagBitsKHR VK_VIDEO_SESSION_CREATE_ALLOW_ENCODE_PARAMETER_OPTIMIZATIONS_BIT_KHR = new VkVideoSessionCreateFlagBitsKHR(2);
    public static final VkVideoSessionCreateFlagBitsKHR VK_VIDEO_SESSION_CREATE_ALLOW_ENCODE_QUANTIZATION_DELTA_MAP_BIT_KHR = new VkVideoSessionCreateFlagBitsKHR(8);
    public static final VkVideoSessionCreateFlagBitsKHR VK_VIDEO_SESSION_CREATE_FLAG_BITS_MAX_ENUM_KHR = new VkVideoSessionCreateFlagBitsKHR(2147483647);
    public static final VkVideoSessionCreateFlagBitsKHR VK_VIDEO_SESSION_CREATE_INLINE_QUERIES_BIT_KHR = new VkVideoSessionCreateFlagBitsKHR(4);
    public static final VkVideoSessionCreateFlagBitsKHR VK_VIDEO_SESSION_CREATE_INLINE_SESSION_PARAMETERS_BIT_KHR = new VkVideoSessionCreateFlagBitsKHR(32);
    public static final VkVideoSessionCreateFlagBitsKHR VK_VIDEO_SESSION_CREATE_PROTECTED_CONTENT_BIT_KHR = new VkVideoSessionCreateFlagBitsKHR(1);

    public static VkVideoSessionCreateFlagBitsKHR fromValue(int value) {
        return switch (value) {
            case 16 -> VK_VIDEO_SESSION_CREATE_ALLOW_ENCODE_EMPHASIS_MAP_BIT_KHR;
            case 2 -> VK_VIDEO_SESSION_CREATE_ALLOW_ENCODE_PARAMETER_OPTIMIZATIONS_BIT_KHR;
            case 8 -> VK_VIDEO_SESSION_CREATE_ALLOW_ENCODE_QUANTIZATION_DELTA_MAP_BIT_KHR;
            case 2147483647 -> VK_VIDEO_SESSION_CREATE_FLAG_BITS_MAX_ENUM_KHR;
            case 4 -> VK_VIDEO_SESSION_CREATE_INLINE_QUERIES_BIT_KHR;
            case 32 -> VK_VIDEO_SESSION_CREATE_INLINE_SESSION_PARAMETERS_BIT_KHR;
            case 1 -> VK_VIDEO_SESSION_CREATE_PROTECTED_CONTENT_BIT_KHR;
            default -> new VkVideoSessionCreateFlagBitsKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
