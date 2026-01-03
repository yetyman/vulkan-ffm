package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkValidationCacheHeaderVersionEXT
 * Generated from jextract bindings
 */
public record VkValidationCacheHeaderVersionEXT(int value) {

    public static final VkValidationCacheHeaderVersionEXT VK_VALIDATION_CACHE_HEADER_VERSION_MAX_ENUM_EXT = new VkValidationCacheHeaderVersionEXT(2147483647);
    public static final VkValidationCacheHeaderVersionEXT VK_VALIDATION_CACHE_HEADER_VERSION_ONE_EXT = new VkValidationCacheHeaderVersionEXT(1);

    public static VkValidationCacheHeaderVersionEXT fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_VALIDATION_CACHE_HEADER_VERSION_MAX_ENUM_EXT;
            case 1 -> VK_VALIDATION_CACHE_HEADER_VERSION_ONE_EXT;
            default -> new VkValidationCacheHeaderVersionEXT(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
