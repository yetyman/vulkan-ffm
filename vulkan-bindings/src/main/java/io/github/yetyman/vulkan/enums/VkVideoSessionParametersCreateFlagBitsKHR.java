package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkVideoSessionParametersCreateFlagBitsKHR
 * Generated from jextract bindings
 */
public record VkVideoSessionParametersCreateFlagBitsKHR(int value) {

    public static final VkVideoSessionParametersCreateFlagBitsKHR VK_VIDEO_SESSION_PARAMETERS_CREATE_FLAG_BITS_MAX_ENUM_KHR = new VkVideoSessionParametersCreateFlagBitsKHR(2147483647);
    public static final VkVideoSessionParametersCreateFlagBitsKHR VK_VIDEO_SESSION_PARAMETERS_CREATE_QUANTIZATION_MAP_COMPATIBLE_BIT_KHR = new VkVideoSessionParametersCreateFlagBitsKHR(1);

    public static VkVideoSessionParametersCreateFlagBitsKHR fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_VIDEO_SESSION_PARAMETERS_CREATE_FLAG_BITS_MAX_ENUM_KHR;
            case 1 -> VK_VIDEO_SESSION_PARAMETERS_CREATE_QUANTIZATION_MAP_COMPATIBLE_BIT_KHR;
            default -> new VkVideoSessionParametersCreateFlagBitsKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
