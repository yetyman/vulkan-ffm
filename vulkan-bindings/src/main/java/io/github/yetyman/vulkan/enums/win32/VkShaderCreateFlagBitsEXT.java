package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkShaderCreateFlagBitsEXT
 * Generated from jextract bindings
 */
public record VkShaderCreateFlagBitsEXT(int value) {

    public static final VkShaderCreateFlagBitsEXT VK_SHADER_CREATE_64_BIT_INDEXING_BIT_EXT = new VkShaderCreateFlagBitsEXT(32768);
    public static final VkShaderCreateFlagBitsEXT VK_SHADER_CREATE_ALLOW_VARYING_SUBGROUP_SIZE_BIT_EXT = new VkShaderCreateFlagBitsEXT(2);
    public static final VkShaderCreateFlagBitsEXT VK_SHADER_CREATE_DISPATCH_BASE_BIT_EXT = new VkShaderCreateFlagBitsEXT(16);
    public static final VkShaderCreateFlagBitsEXT VK_SHADER_CREATE_FLAG_BITS_MAX_ENUM_EXT = new VkShaderCreateFlagBitsEXT(2147483647);
    public static final VkShaderCreateFlagBitsEXT VK_SHADER_CREATE_FRAGMENT_DENSITY_MAP_ATTACHMENT_BIT_EXT = new VkShaderCreateFlagBitsEXT(64);
    public static final VkShaderCreateFlagBitsEXT VK_SHADER_CREATE_FRAGMENT_SHADING_RATE_ATTACHMENT_BIT_EXT = new VkShaderCreateFlagBitsEXT(32);
    public static final VkShaderCreateFlagBitsEXT VK_SHADER_CREATE_INDIRECT_BINDABLE_BIT_EXT = new VkShaderCreateFlagBitsEXT(128);
    public static final VkShaderCreateFlagBitsEXT VK_SHADER_CREATE_LINK_STAGE_BIT_EXT = new VkShaderCreateFlagBitsEXT(1);
    public static final VkShaderCreateFlagBitsEXT VK_SHADER_CREATE_NO_TASK_SHADER_BIT_EXT = new VkShaderCreateFlagBitsEXT(8);
    public static final VkShaderCreateFlagBitsEXT VK_SHADER_CREATE_REQUIRE_FULL_SUBGROUPS_BIT_EXT = new VkShaderCreateFlagBitsEXT(4);

    public static VkShaderCreateFlagBitsEXT fromValue(int value) {
        return switch (value) {
            case 32768 -> VK_SHADER_CREATE_64_BIT_INDEXING_BIT_EXT;
            case 2 -> VK_SHADER_CREATE_ALLOW_VARYING_SUBGROUP_SIZE_BIT_EXT;
            case 16 -> VK_SHADER_CREATE_DISPATCH_BASE_BIT_EXT;
            case 2147483647 -> VK_SHADER_CREATE_FLAG_BITS_MAX_ENUM_EXT;
            case 64 -> VK_SHADER_CREATE_FRAGMENT_DENSITY_MAP_ATTACHMENT_BIT_EXT;
            case 32 -> VK_SHADER_CREATE_FRAGMENT_SHADING_RATE_ATTACHMENT_BIT_EXT;
            case 128 -> VK_SHADER_CREATE_INDIRECT_BINDABLE_BIT_EXT;
            case 1 -> VK_SHADER_CREATE_LINK_STAGE_BIT_EXT;
            case 8 -> VK_SHADER_CREATE_NO_TASK_SHADER_BIT_EXT;
            case 4 -> VK_SHADER_CREATE_REQUIRE_FULL_SUBGROUPS_BIT_EXT;
            default -> new VkShaderCreateFlagBitsEXT(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
