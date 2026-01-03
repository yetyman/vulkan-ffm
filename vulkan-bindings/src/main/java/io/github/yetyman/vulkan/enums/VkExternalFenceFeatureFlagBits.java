package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkExternalFenceFeatureFlagBits
 * Generated from jextract bindings
 */
public record VkExternalFenceFeatureFlagBits(int value) {

    public static final VkExternalFenceFeatureFlagBits VK_EXTERNAL_FENCE_FEATURE_EXPORTABLE_BIT = new VkExternalFenceFeatureFlagBits(1);
    public static final VkExternalFenceFeatureFlagBits VK_EXTERNAL_FENCE_FEATURE_EXPORTABLE_BIT_KHR = VK_EXTERNAL_FENCE_FEATURE_EXPORTABLE_BIT;
    public static final VkExternalFenceFeatureFlagBits VK_EXTERNAL_FENCE_FEATURE_FLAG_BITS_MAX_ENUM = new VkExternalFenceFeatureFlagBits(2147483647);
    public static final VkExternalFenceFeatureFlagBits VK_EXTERNAL_FENCE_FEATURE_IMPORTABLE_BIT = new VkExternalFenceFeatureFlagBits(2);
    public static final VkExternalFenceFeatureFlagBits VK_EXTERNAL_FENCE_FEATURE_IMPORTABLE_BIT_KHR = VK_EXTERNAL_FENCE_FEATURE_IMPORTABLE_BIT;

    public static VkExternalFenceFeatureFlagBits fromValue(int value) {
        return switch (value) {
            case 1 -> VK_EXTERNAL_FENCE_FEATURE_EXPORTABLE_BIT;
            case 2147483647 -> VK_EXTERNAL_FENCE_FEATURE_FLAG_BITS_MAX_ENUM;
            case 2 -> VK_EXTERNAL_FENCE_FEATURE_IMPORTABLE_BIT;
            default -> new VkExternalFenceFeatureFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
