package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkShaderCodeTypeEXT
 * Generated from jextract bindings
 */
public record VkShaderCodeTypeEXT(int value) {

    public static final VkShaderCodeTypeEXT VK_SHADER_CODE_TYPE_BINARY_EXT = new VkShaderCodeTypeEXT(0);
    public static final VkShaderCodeTypeEXT VK_SHADER_CODE_TYPE_MAX_ENUM_EXT = new VkShaderCodeTypeEXT(2147483647);
    public static final VkShaderCodeTypeEXT VK_SHADER_CODE_TYPE_SPIRV_EXT = new VkShaderCodeTypeEXT(1);

    public static VkShaderCodeTypeEXT fromValue(int value) {
        return switch (value) {
            case 0 -> VK_SHADER_CODE_TYPE_BINARY_EXT;
            case 2147483647 -> VK_SHADER_CODE_TYPE_MAX_ENUM_EXT;
            case 1 -> VK_SHADER_CODE_TYPE_SPIRV_EXT;
            default -> new VkShaderCodeTypeEXT(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
