package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkIndirectExecutionSetInfoTypeEXT
 * Generated from jextract bindings
 */
public record VkIndirectExecutionSetInfoTypeEXT(int value) {

    public static final VkIndirectExecutionSetInfoTypeEXT VK_INDIRECT_EXECUTION_SET_INFO_TYPE_MAX_ENUM_EXT = new VkIndirectExecutionSetInfoTypeEXT(2147483647);
    public static final VkIndirectExecutionSetInfoTypeEXT VK_INDIRECT_EXECUTION_SET_INFO_TYPE_PIPELINES_EXT = new VkIndirectExecutionSetInfoTypeEXT(0);
    public static final VkIndirectExecutionSetInfoTypeEXT VK_INDIRECT_EXECUTION_SET_INFO_TYPE_SHADER_OBJECTS_EXT = new VkIndirectExecutionSetInfoTypeEXT(1);

    public static VkIndirectExecutionSetInfoTypeEXT fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_INDIRECT_EXECUTION_SET_INFO_TYPE_MAX_ENUM_EXT;
            case 0 -> VK_INDIRECT_EXECUTION_SET_INFO_TYPE_PIPELINES_EXT;
            case 1 -> VK_INDIRECT_EXECUTION_SET_INFO_TYPE_SHADER_OBJECTS_EXT;
            default -> new VkIndirectExecutionSetInfoTypeEXT(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
