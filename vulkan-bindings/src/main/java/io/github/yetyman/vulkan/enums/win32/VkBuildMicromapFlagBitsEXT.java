package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkBuildMicromapFlagBitsEXT
 * Generated from jextract bindings
 */
public record VkBuildMicromapFlagBitsEXT(int value) {

    public static final VkBuildMicromapFlagBitsEXT VK_BUILD_MICROMAP_ALLOW_COMPACTION_BIT_EXT = new VkBuildMicromapFlagBitsEXT(4);
    public static final VkBuildMicromapFlagBitsEXT VK_BUILD_MICROMAP_FLAG_BITS_MAX_ENUM_EXT = new VkBuildMicromapFlagBitsEXT(2147483647);
    public static final VkBuildMicromapFlagBitsEXT VK_BUILD_MICROMAP_PREFER_FAST_BUILD_BIT_EXT = new VkBuildMicromapFlagBitsEXT(2);
    public static final VkBuildMicromapFlagBitsEXT VK_BUILD_MICROMAP_PREFER_FAST_TRACE_BIT_EXT = new VkBuildMicromapFlagBitsEXT(1);

    public static VkBuildMicromapFlagBitsEXT fromValue(int value) {
        return switch (value) {
            case 4 -> VK_BUILD_MICROMAP_ALLOW_COMPACTION_BIT_EXT;
            case 2147483647 -> VK_BUILD_MICROMAP_FLAG_BITS_MAX_ENUM_EXT;
            case 2 -> VK_BUILD_MICROMAP_PREFER_FAST_BUILD_BIT_EXT;
            case 1 -> VK_BUILD_MICROMAP_PREFER_FAST_TRACE_BIT_EXT;
            default -> new VkBuildMicromapFlagBitsEXT(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
