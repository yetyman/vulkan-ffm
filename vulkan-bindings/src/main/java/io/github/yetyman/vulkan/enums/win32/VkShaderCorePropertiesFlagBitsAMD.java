package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkShaderCorePropertiesFlagBitsAMD
 * Generated from jextract bindings
 */
public record VkShaderCorePropertiesFlagBitsAMD(int value) {

    public static final VkShaderCorePropertiesFlagBitsAMD VK_SHADER_CORE_PROPERTIES_FLAG_BITS_MAX_ENUM_AMD = new VkShaderCorePropertiesFlagBitsAMD(2147483647);

    public static VkShaderCorePropertiesFlagBitsAMD fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_SHADER_CORE_PROPERTIES_FLAG_BITS_MAX_ENUM_AMD;
            default -> new VkShaderCorePropertiesFlagBitsAMD(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
