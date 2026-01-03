package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkShaderInfoTypeAMD
 * Generated from jextract bindings
 */
public record VkShaderInfoTypeAMD(int value) {

    public static final VkShaderInfoTypeAMD VK_SHADER_INFO_TYPE_BINARY_AMD = new VkShaderInfoTypeAMD(1);
    public static final VkShaderInfoTypeAMD VK_SHADER_INFO_TYPE_DISASSEMBLY_AMD = new VkShaderInfoTypeAMD(2);
    public static final VkShaderInfoTypeAMD VK_SHADER_INFO_TYPE_MAX_ENUM_AMD = new VkShaderInfoTypeAMD(2147483647);
    public static final VkShaderInfoTypeAMD VK_SHADER_INFO_TYPE_STATISTICS_AMD = new VkShaderInfoTypeAMD(0);

    public static VkShaderInfoTypeAMD fromValue(int value) {
        return switch (value) {
            case 1 -> VK_SHADER_INFO_TYPE_BINARY_AMD;
            case 2 -> VK_SHADER_INFO_TYPE_DISASSEMBLY_AMD;
            case 2147483647 -> VK_SHADER_INFO_TYPE_MAX_ENUM_AMD;
            case 0 -> VK_SHADER_INFO_TYPE_STATISTICS_AMD;
            default -> new VkShaderInfoTypeAMD(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
