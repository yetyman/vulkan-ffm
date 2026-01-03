package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkValidationFeatureDisableEXT
 * Generated from jextract bindings
 */
public record VkValidationFeatureDisableEXT(int value) {

    public static final VkValidationFeatureDisableEXT VK_VALIDATION_FEATURE_DISABLE_ALL_EXT = new VkValidationFeatureDisableEXT(0);
    public static final VkValidationFeatureDisableEXT VK_VALIDATION_FEATURE_DISABLE_API_PARAMETERS_EXT = new VkValidationFeatureDisableEXT(3);
    public static final VkValidationFeatureDisableEXT VK_VALIDATION_FEATURE_DISABLE_CORE_CHECKS_EXT = new VkValidationFeatureDisableEXT(5);
    public static final VkValidationFeatureDisableEXT VK_VALIDATION_FEATURE_DISABLE_MAX_ENUM_EXT = new VkValidationFeatureDisableEXT(2147483647);
    public static final VkValidationFeatureDisableEXT VK_VALIDATION_FEATURE_DISABLE_OBJECT_LIFETIMES_EXT = new VkValidationFeatureDisableEXT(4);
    public static final VkValidationFeatureDisableEXT VK_VALIDATION_FEATURE_DISABLE_SHADERS_EXT = new VkValidationFeatureDisableEXT(1);
    public static final VkValidationFeatureDisableEXT VK_VALIDATION_FEATURE_DISABLE_SHADER_VALIDATION_CACHE_EXT = new VkValidationFeatureDisableEXT(7);
    public static final VkValidationFeatureDisableEXT VK_VALIDATION_FEATURE_DISABLE_THREAD_SAFETY_EXT = new VkValidationFeatureDisableEXT(2);
    public static final VkValidationFeatureDisableEXT VK_VALIDATION_FEATURE_DISABLE_UNIQUE_HANDLES_EXT = new VkValidationFeatureDisableEXT(6);

    public static VkValidationFeatureDisableEXT fromValue(int value) {
        return switch (value) {
            case 0 -> VK_VALIDATION_FEATURE_DISABLE_ALL_EXT;
            case 3 -> VK_VALIDATION_FEATURE_DISABLE_API_PARAMETERS_EXT;
            case 5 -> VK_VALIDATION_FEATURE_DISABLE_CORE_CHECKS_EXT;
            case 2147483647 -> VK_VALIDATION_FEATURE_DISABLE_MAX_ENUM_EXT;
            case 4 -> VK_VALIDATION_FEATURE_DISABLE_OBJECT_LIFETIMES_EXT;
            case 1 -> VK_VALIDATION_FEATURE_DISABLE_SHADERS_EXT;
            case 7 -> VK_VALIDATION_FEATURE_DISABLE_SHADER_VALIDATION_CACHE_EXT;
            case 2 -> VK_VALIDATION_FEATURE_DISABLE_THREAD_SAFETY_EXT;
            case 6 -> VK_VALIDATION_FEATURE_DISABLE_UNIQUE_HANDLES_EXT;
            default -> new VkValidationFeatureDisableEXT(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
