package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkValidationCheckEXT
 * Generated from jextract bindings
 */
public record VkValidationCheckEXT(int value) {

    public static final VkValidationCheckEXT VK_VALIDATION_CHECK_ALL_EXT = new VkValidationCheckEXT(0);
    public static final VkValidationCheckEXT VK_VALIDATION_CHECK_MAX_ENUM_EXT = new VkValidationCheckEXT(2147483647);
    public static final VkValidationCheckEXT VK_VALIDATION_CHECK_SHADERS_EXT = new VkValidationCheckEXT(1);

    public static VkValidationCheckEXT fromValue(int value) {
        return switch (value) {
            case 0 -> VK_VALIDATION_CHECK_ALL_EXT;
            case 2147483647 -> VK_VALIDATION_CHECK_MAX_ENUM_EXT;
            case 1 -> VK_VALIDATION_CHECK_SHADERS_EXT;
            default -> new VkValidationCheckEXT(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
