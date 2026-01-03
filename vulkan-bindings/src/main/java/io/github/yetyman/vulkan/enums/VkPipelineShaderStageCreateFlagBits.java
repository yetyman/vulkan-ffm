package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkPipelineShaderStageCreateFlagBits
 * Generated from jextract bindings
 */
public record VkPipelineShaderStageCreateFlagBits(int value) {

    public static final VkPipelineShaderStageCreateFlagBits VK_PIPELINE_SHADER_STAGE_CREATE_ALLOW_VARYING_SUBGROUP_SIZE_BIT = new VkPipelineShaderStageCreateFlagBits(1);
    public static final VkPipelineShaderStageCreateFlagBits VK_PIPELINE_SHADER_STAGE_CREATE_ALLOW_VARYING_SUBGROUP_SIZE_BIT_EXT = VK_PIPELINE_SHADER_STAGE_CREATE_ALLOW_VARYING_SUBGROUP_SIZE_BIT;
    public static final VkPipelineShaderStageCreateFlagBits VK_PIPELINE_SHADER_STAGE_CREATE_FLAG_BITS_MAX_ENUM = new VkPipelineShaderStageCreateFlagBits(2147483647);
    public static final VkPipelineShaderStageCreateFlagBits VK_PIPELINE_SHADER_STAGE_CREATE_REQUIRE_FULL_SUBGROUPS_BIT = new VkPipelineShaderStageCreateFlagBits(2);
    public static final VkPipelineShaderStageCreateFlagBits VK_PIPELINE_SHADER_STAGE_CREATE_REQUIRE_FULL_SUBGROUPS_BIT_EXT = VK_PIPELINE_SHADER_STAGE_CREATE_REQUIRE_FULL_SUBGROUPS_BIT;

    public static VkPipelineShaderStageCreateFlagBits fromValue(int value) {
        return switch (value) {
            case 1 -> VK_PIPELINE_SHADER_STAGE_CREATE_ALLOW_VARYING_SUBGROUP_SIZE_BIT;
            case 2147483647 -> VK_PIPELINE_SHADER_STAGE_CREATE_FLAG_BITS_MAX_ENUM;
            case 2 -> VK_PIPELINE_SHADER_STAGE_CREATE_REQUIRE_FULL_SUBGROUPS_BIT;
            default -> new VkPipelineShaderStageCreateFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
