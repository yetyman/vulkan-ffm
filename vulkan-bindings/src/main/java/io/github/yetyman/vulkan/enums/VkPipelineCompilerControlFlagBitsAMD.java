package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkPipelineCompilerControlFlagBitsAMD
 * Generated from jextract bindings
 */
public record VkPipelineCompilerControlFlagBitsAMD(int value) {

    public static final VkPipelineCompilerControlFlagBitsAMD VK_PIPELINE_COMPILER_CONTROL_FLAG_BITS_MAX_ENUM_AMD = new VkPipelineCompilerControlFlagBitsAMD(2147483647);

    public static VkPipelineCompilerControlFlagBitsAMD fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_PIPELINE_COMPILER_CONTROL_FLAG_BITS_MAX_ENUM_AMD;
            default -> new VkPipelineCompilerControlFlagBitsAMD(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
