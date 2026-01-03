package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkExternalMemoryFeatureFlagBits
 * Generated from jextract bindings
 */
public record VkExternalMemoryFeatureFlagBits(int value) {

    public static final VkExternalMemoryFeatureFlagBits VK_EXTERNAL_MEMORY_FEATURE_DEDICATED_ONLY_BIT = new VkExternalMemoryFeatureFlagBits(1);
    public static final VkExternalMemoryFeatureFlagBits VK_EXTERNAL_MEMORY_FEATURE_DEDICATED_ONLY_BIT_KHR = VK_EXTERNAL_MEMORY_FEATURE_DEDICATED_ONLY_BIT;
    public static final VkExternalMemoryFeatureFlagBits VK_EXTERNAL_MEMORY_FEATURE_EXPORTABLE_BIT = new VkExternalMemoryFeatureFlagBits(2);
    public static final VkExternalMemoryFeatureFlagBits VK_EXTERNAL_MEMORY_FEATURE_EXPORTABLE_BIT_KHR = VK_EXTERNAL_MEMORY_FEATURE_EXPORTABLE_BIT;
    public static final VkExternalMemoryFeatureFlagBits VK_EXTERNAL_MEMORY_FEATURE_FLAG_BITS_MAX_ENUM = new VkExternalMemoryFeatureFlagBits(2147483647);
    public static final VkExternalMemoryFeatureFlagBits VK_EXTERNAL_MEMORY_FEATURE_IMPORTABLE_BIT = new VkExternalMemoryFeatureFlagBits(4);
    public static final VkExternalMemoryFeatureFlagBits VK_EXTERNAL_MEMORY_FEATURE_IMPORTABLE_BIT_KHR = VK_EXTERNAL_MEMORY_FEATURE_IMPORTABLE_BIT;

    public static VkExternalMemoryFeatureFlagBits fromValue(int value) {
        return switch (value) {
            case 1 -> VK_EXTERNAL_MEMORY_FEATURE_DEDICATED_ONLY_BIT;
            case 2 -> VK_EXTERNAL_MEMORY_FEATURE_EXPORTABLE_BIT;
            case 2147483647 -> VK_EXTERNAL_MEMORY_FEATURE_FLAG_BITS_MAX_ENUM;
            case 4 -> VK_EXTERNAL_MEMORY_FEATURE_IMPORTABLE_BIT;
            default -> new VkExternalMemoryFeatureFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
