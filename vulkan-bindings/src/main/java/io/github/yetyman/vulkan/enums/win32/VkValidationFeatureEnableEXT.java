package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkValidationFeatureEnableEXT
 * Generated from jextract bindings
 */
public record VkValidationFeatureEnableEXT(int value) {

    public static final VkValidationFeatureEnableEXT VK_VALIDATION_FEATURE_ENABLE_BEST_PRACTICES_EXT = new VkValidationFeatureEnableEXT(2);
    public static final VkValidationFeatureEnableEXT VK_VALIDATION_FEATURE_ENABLE_DEBUG_PRINTF_EXT = new VkValidationFeatureEnableEXT(3);
    public static final VkValidationFeatureEnableEXT VK_VALIDATION_FEATURE_ENABLE_GPU_ASSISTED_EXT = new VkValidationFeatureEnableEXT(0);
    public static final VkValidationFeatureEnableEXT VK_VALIDATION_FEATURE_ENABLE_GPU_ASSISTED_RESERVE_BINDING_SLOT_EXT = new VkValidationFeatureEnableEXT(1);
    public static final VkValidationFeatureEnableEXT VK_VALIDATION_FEATURE_ENABLE_MAX_ENUM_EXT = new VkValidationFeatureEnableEXT(2147483647);
    public static final VkValidationFeatureEnableEXT VK_VALIDATION_FEATURE_ENABLE_SYNCHRONIZATION_VALIDATION_EXT = new VkValidationFeatureEnableEXT(4);

    public static VkValidationFeatureEnableEXT fromValue(int value) {
        return switch (value) {
            case 2 -> VK_VALIDATION_FEATURE_ENABLE_BEST_PRACTICES_EXT;
            case 3 -> VK_VALIDATION_FEATURE_ENABLE_DEBUG_PRINTF_EXT;
            case 0 -> VK_VALIDATION_FEATURE_ENABLE_GPU_ASSISTED_EXT;
            case 1 -> VK_VALIDATION_FEATURE_ENABLE_GPU_ASSISTED_RESERVE_BINDING_SLOT_EXT;
            case 2147483647 -> VK_VALIDATION_FEATURE_ENABLE_MAX_ENUM_EXT;
            case 4 -> VK_VALIDATION_FEATURE_ENABLE_SYNCHRONIZATION_VALIDATION_EXT;
            default -> new VkValidationFeatureEnableEXT(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
