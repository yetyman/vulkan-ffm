package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkExternalMemoryFeatureFlagBitsNV
 * Generated from jextract bindings
 */
public record VkExternalMemoryFeatureFlagBitsNV(int value) {

    public static final VkExternalMemoryFeatureFlagBitsNV VK_EXTERNAL_MEMORY_FEATURE_DEDICATED_ONLY_BIT_NV = new VkExternalMemoryFeatureFlagBitsNV(1);
    public static final VkExternalMemoryFeatureFlagBitsNV VK_EXTERNAL_MEMORY_FEATURE_EXPORTABLE_BIT_NV = new VkExternalMemoryFeatureFlagBitsNV(2);
    public static final VkExternalMemoryFeatureFlagBitsNV VK_EXTERNAL_MEMORY_FEATURE_FLAG_BITS_MAX_ENUM_NV = new VkExternalMemoryFeatureFlagBitsNV(2147483647);
    public static final VkExternalMemoryFeatureFlagBitsNV VK_EXTERNAL_MEMORY_FEATURE_IMPORTABLE_BIT_NV = new VkExternalMemoryFeatureFlagBitsNV(4);

    public static VkExternalMemoryFeatureFlagBitsNV fromValue(int value) {
        return switch (value) {
            case 1 -> VK_EXTERNAL_MEMORY_FEATURE_DEDICATED_ONLY_BIT_NV;
            case 2 -> VK_EXTERNAL_MEMORY_FEATURE_EXPORTABLE_BIT_NV;
            case 2147483647 -> VK_EXTERNAL_MEMORY_FEATURE_FLAG_BITS_MAX_ENUM_NV;
            case 4 -> VK_EXTERNAL_MEMORY_FEATURE_IMPORTABLE_BIT_NV;
            default -> new VkExternalMemoryFeatureFlagBitsNV(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
