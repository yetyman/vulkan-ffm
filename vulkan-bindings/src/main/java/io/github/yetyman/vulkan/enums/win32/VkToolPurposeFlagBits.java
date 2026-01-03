package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkToolPurposeFlagBits
 * Generated from jextract bindings
 */
public record VkToolPurposeFlagBits(int value) {

    public static final VkToolPurposeFlagBits VK_TOOL_PURPOSE_ADDITIONAL_FEATURES_BIT = new VkToolPurposeFlagBits(8);
    public static final VkToolPurposeFlagBits VK_TOOL_PURPOSE_ADDITIONAL_FEATURES_BIT_EXT = new VkToolPurposeFlagBits(8);
    public static final VkToolPurposeFlagBits VK_TOOL_PURPOSE_DEBUG_MARKERS_BIT_EXT = new VkToolPurposeFlagBits(64);
    public static final VkToolPurposeFlagBits VK_TOOL_PURPOSE_DEBUG_REPORTING_BIT_EXT = new VkToolPurposeFlagBits(32);
    public static final VkToolPurposeFlagBits VK_TOOL_PURPOSE_FLAG_BITS_MAX_ENUM = new VkToolPurposeFlagBits(2147483647);
    public static final VkToolPurposeFlagBits VK_TOOL_PURPOSE_MODIFYING_FEATURES_BIT = new VkToolPurposeFlagBits(16);
    public static final VkToolPurposeFlagBits VK_TOOL_PURPOSE_MODIFYING_FEATURES_BIT_EXT = new VkToolPurposeFlagBits(16);
    public static final VkToolPurposeFlagBits VK_TOOL_PURPOSE_PROFILING_BIT = new VkToolPurposeFlagBits(2);
    public static final VkToolPurposeFlagBits VK_TOOL_PURPOSE_PROFILING_BIT_EXT = new VkToolPurposeFlagBits(2);
    public static final VkToolPurposeFlagBits VK_TOOL_PURPOSE_TRACING_BIT = new VkToolPurposeFlagBits(4);
    public static final VkToolPurposeFlagBits VK_TOOL_PURPOSE_TRACING_BIT_EXT = new VkToolPurposeFlagBits(4);
    public static final VkToolPurposeFlagBits VK_TOOL_PURPOSE_VALIDATION_BIT = new VkToolPurposeFlagBits(1);
    public static final VkToolPurposeFlagBits VK_TOOL_PURPOSE_VALIDATION_BIT_EXT = new VkToolPurposeFlagBits(1);

    public static VkToolPurposeFlagBits fromValue(int value) {
        return switch (value) {
            case 8 -> VK_TOOL_PURPOSE_ADDITIONAL_FEATURES_BIT;
            case 64 -> VK_TOOL_PURPOSE_DEBUG_MARKERS_BIT_EXT;
            case 32 -> VK_TOOL_PURPOSE_DEBUG_REPORTING_BIT_EXT;
            case 2147483647 -> VK_TOOL_PURPOSE_FLAG_BITS_MAX_ENUM;
            case 16 -> VK_TOOL_PURPOSE_MODIFYING_FEATURES_BIT;
            case 2 -> VK_TOOL_PURPOSE_PROFILING_BIT;
            case 4 -> VK_TOOL_PURPOSE_TRACING_BIT;
            case 1 -> VK_TOOL_PURPOSE_VALIDATION_BIT;
            default -> new VkToolPurposeFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
