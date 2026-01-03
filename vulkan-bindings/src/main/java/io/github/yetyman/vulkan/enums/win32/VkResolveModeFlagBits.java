package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkResolveModeFlagBits
 * Generated from jextract bindings
 */
public record VkResolveModeFlagBits(int value) {

    public static final VkResolveModeFlagBits VK_RESOLVE_MODE_AVERAGE_BIT = new VkResolveModeFlagBits(2);
    public static final VkResolveModeFlagBits VK_RESOLVE_MODE_AVERAGE_BIT_KHR = new VkResolveModeFlagBits(2);
    public static final VkResolveModeFlagBits VK_RESOLVE_MODE_CUSTOM_BIT_EXT = new VkResolveModeFlagBits(32);
    public static final VkResolveModeFlagBits VK_RESOLVE_MODE_EXTERNAL_FORMAT_DOWNSAMPLE_ANDROID = new VkResolveModeFlagBits(16);
    public static final VkResolveModeFlagBits VK_RESOLVE_MODE_EXTERNAL_FORMAT_DOWNSAMPLE_BIT_ANDROID = new VkResolveModeFlagBits(16);
    public static final VkResolveModeFlagBits VK_RESOLVE_MODE_FLAG_BITS_MAX_ENUM = new VkResolveModeFlagBits(2147483647);
    public static final VkResolveModeFlagBits VK_RESOLVE_MODE_MAX_BIT = new VkResolveModeFlagBits(8);
    public static final VkResolveModeFlagBits VK_RESOLVE_MODE_MAX_BIT_KHR = new VkResolveModeFlagBits(8);
    public static final VkResolveModeFlagBits VK_RESOLVE_MODE_MIN_BIT = new VkResolveModeFlagBits(4);
    public static final VkResolveModeFlagBits VK_RESOLVE_MODE_MIN_BIT_KHR = new VkResolveModeFlagBits(4);
    public static final VkResolveModeFlagBits VK_RESOLVE_MODE_NONE = new VkResolveModeFlagBits(0);
    public static final VkResolveModeFlagBits VK_RESOLVE_MODE_NONE_KHR = new VkResolveModeFlagBits(0);
    public static final VkResolveModeFlagBits VK_RESOLVE_MODE_SAMPLE_ZERO_BIT = new VkResolveModeFlagBits(1);
    public static final VkResolveModeFlagBits VK_RESOLVE_MODE_SAMPLE_ZERO_BIT_KHR = new VkResolveModeFlagBits(1);

    public static VkResolveModeFlagBits fromValue(int value) {
        return switch (value) {
            case 2 -> VK_RESOLVE_MODE_AVERAGE_BIT;
            case 32 -> VK_RESOLVE_MODE_CUSTOM_BIT_EXT;
            case 16 -> VK_RESOLVE_MODE_EXTERNAL_FORMAT_DOWNSAMPLE_ANDROID;
            case 2147483647 -> VK_RESOLVE_MODE_FLAG_BITS_MAX_ENUM;
            case 8 -> VK_RESOLVE_MODE_MAX_BIT;
            case 4 -> VK_RESOLVE_MODE_MIN_BIT;
            case 0 -> VK_RESOLVE_MODE_NONE;
            case 1 -> VK_RESOLVE_MODE_SAMPLE_ZERO_BIT;
            default -> new VkResolveModeFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
