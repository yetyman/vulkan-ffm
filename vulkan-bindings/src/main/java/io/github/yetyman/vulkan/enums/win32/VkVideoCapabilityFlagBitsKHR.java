package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkVideoCapabilityFlagBitsKHR
 * Generated from jextract bindings
 */
public record VkVideoCapabilityFlagBitsKHR(int value) {

    public static final VkVideoCapabilityFlagBitsKHR VK_VIDEO_CAPABILITY_FLAG_BITS_MAX_ENUM_KHR = new VkVideoCapabilityFlagBitsKHR(2147483647);
    public static final VkVideoCapabilityFlagBitsKHR VK_VIDEO_CAPABILITY_PROTECTED_CONTENT_BIT_KHR = new VkVideoCapabilityFlagBitsKHR(1);
    public static final VkVideoCapabilityFlagBitsKHR VK_VIDEO_CAPABILITY_SEPARATE_REFERENCE_IMAGES_BIT_KHR = new VkVideoCapabilityFlagBitsKHR(2);

    public static VkVideoCapabilityFlagBitsKHR fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_VIDEO_CAPABILITY_FLAG_BITS_MAX_ENUM_KHR;
            case 1 -> VK_VIDEO_CAPABILITY_PROTECTED_CONTENT_BIT_KHR;
            case 2 -> VK_VIDEO_CAPABILITY_SEPARATE_REFERENCE_IMAGES_BIT_KHR;
            default -> new VkVideoCapabilityFlagBitsKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
