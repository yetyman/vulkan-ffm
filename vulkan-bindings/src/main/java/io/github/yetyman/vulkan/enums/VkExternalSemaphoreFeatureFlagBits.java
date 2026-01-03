package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkExternalSemaphoreFeatureFlagBits
 * Generated from jextract bindings
 */
public record VkExternalSemaphoreFeatureFlagBits(int value) {

    public static final VkExternalSemaphoreFeatureFlagBits VK_EXTERNAL_SEMAPHORE_FEATURE_EXPORTABLE_BIT = new VkExternalSemaphoreFeatureFlagBits(1);
    public static final VkExternalSemaphoreFeatureFlagBits VK_EXTERNAL_SEMAPHORE_FEATURE_EXPORTABLE_BIT_KHR = VK_EXTERNAL_SEMAPHORE_FEATURE_EXPORTABLE_BIT;
    public static final VkExternalSemaphoreFeatureFlagBits VK_EXTERNAL_SEMAPHORE_FEATURE_FLAG_BITS_MAX_ENUM = new VkExternalSemaphoreFeatureFlagBits(2147483647);
    public static final VkExternalSemaphoreFeatureFlagBits VK_EXTERNAL_SEMAPHORE_FEATURE_IMPORTABLE_BIT = new VkExternalSemaphoreFeatureFlagBits(2);
    public static final VkExternalSemaphoreFeatureFlagBits VK_EXTERNAL_SEMAPHORE_FEATURE_IMPORTABLE_BIT_KHR = VK_EXTERNAL_SEMAPHORE_FEATURE_IMPORTABLE_BIT;

    public static VkExternalSemaphoreFeatureFlagBits fromValue(int value) {
        return switch (value) {
            case 1 -> VK_EXTERNAL_SEMAPHORE_FEATURE_EXPORTABLE_BIT;
            case 2147483647 -> VK_EXTERNAL_SEMAPHORE_FEATURE_FLAG_BITS_MAX_ENUM;
            case 2 -> VK_EXTERNAL_SEMAPHORE_FEATURE_IMPORTABLE_BIT;
            default -> new VkExternalSemaphoreFeatureFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
