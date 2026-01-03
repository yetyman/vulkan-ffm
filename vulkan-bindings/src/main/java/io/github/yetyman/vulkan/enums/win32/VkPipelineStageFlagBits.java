package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkPipelineStageFlagBits
 * Generated from jextract bindings
 */
public record VkPipelineStageFlagBits(int value) {

    public static final VkPipelineStageFlagBits VK_PIPELINE_STAGE_ACCELERATION_STRUCTURE_BUILD_BIT_KHR = new VkPipelineStageFlagBits(33554432);
    public static final VkPipelineStageFlagBits VK_PIPELINE_STAGE_ACCELERATION_STRUCTURE_BUILD_BIT_NV = new VkPipelineStageFlagBits(33554432);
    public static final VkPipelineStageFlagBits VK_PIPELINE_STAGE_ALL_COMMANDS_BIT = new VkPipelineStageFlagBits(65536);
    public static final VkPipelineStageFlagBits VK_PIPELINE_STAGE_ALL_GRAPHICS_BIT = new VkPipelineStageFlagBits(32768);
    public static final VkPipelineStageFlagBits VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT = new VkPipelineStageFlagBits(8192);
    public static final VkPipelineStageFlagBits VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT = new VkPipelineStageFlagBits(1024);
    public static final VkPipelineStageFlagBits VK_PIPELINE_STAGE_COMMAND_PREPROCESS_BIT_EXT = new VkPipelineStageFlagBits(131072);
    public static final VkPipelineStageFlagBits VK_PIPELINE_STAGE_COMMAND_PREPROCESS_BIT_NV = new VkPipelineStageFlagBits(131072);
    public static final VkPipelineStageFlagBits VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT = new VkPipelineStageFlagBits(2048);
    public static final VkPipelineStageFlagBits VK_PIPELINE_STAGE_CONDITIONAL_RENDERING_BIT_EXT = new VkPipelineStageFlagBits(262144);
    public static final VkPipelineStageFlagBits VK_PIPELINE_STAGE_DRAW_INDIRECT_BIT = new VkPipelineStageFlagBits(2);
    public static final VkPipelineStageFlagBits VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT = new VkPipelineStageFlagBits(256);
    public static final VkPipelineStageFlagBits VK_PIPELINE_STAGE_FLAG_BITS_MAX_ENUM = new VkPipelineStageFlagBits(2147483647);
    public static final VkPipelineStageFlagBits VK_PIPELINE_STAGE_FRAGMENT_DENSITY_PROCESS_BIT_EXT = new VkPipelineStageFlagBits(8388608);
    public static final VkPipelineStageFlagBits VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT = new VkPipelineStageFlagBits(128);
    public static final VkPipelineStageFlagBits VK_PIPELINE_STAGE_FRAGMENT_SHADING_RATE_ATTACHMENT_BIT_KHR = new VkPipelineStageFlagBits(4194304);
    public static final VkPipelineStageFlagBits VK_PIPELINE_STAGE_GEOMETRY_SHADER_BIT = new VkPipelineStageFlagBits(64);
    public static final VkPipelineStageFlagBits VK_PIPELINE_STAGE_HOST_BIT = new VkPipelineStageFlagBits(16384);
    public static final VkPipelineStageFlagBits VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT = new VkPipelineStageFlagBits(512);
    public static final VkPipelineStageFlagBits VK_PIPELINE_STAGE_MESH_SHADER_BIT_EXT = new VkPipelineStageFlagBits(1048576);
    public static final VkPipelineStageFlagBits VK_PIPELINE_STAGE_MESH_SHADER_BIT_NV = new VkPipelineStageFlagBits(1048576);
    public static final VkPipelineStageFlagBits VK_PIPELINE_STAGE_NONE = new VkPipelineStageFlagBits(0);
    public static final VkPipelineStageFlagBits VK_PIPELINE_STAGE_NONE_KHR = new VkPipelineStageFlagBits(0);
    public static final VkPipelineStageFlagBits VK_PIPELINE_STAGE_RAY_TRACING_SHADER_BIT_KHR = new VkPipelineStageFlagBits(2097152);
    public static final VkPipelineStageFlagBits VK_PIPELINE_STAGE_RAY_TRACING_SHADER_BIT_NV = new VkPipelineStageFlagBits(2097152);
    public static final VkPipelineStageFlagBits VK_PIPELINE_STAGE_SHADING_RATE_IMAGE_BIT_NV = new VkPipelineStageFlagBits(4194304);
    public static final VkPipelineStageFlagBits VK_PIPELINE_STAGE_TASK_SHADER_BIT_EXT = new VkPipelineStageFlagBits(524288);
    public static final VkPipelineStageFlagBits VK_PIPELINE_STAGE_TASK_SHADER_BIT_NV = new VkPipelineStageFlagBits(524288);
    public static final VkPipelineStageFlagBits VK_PIPELINE_STAGE_TESSELLATION_CONTROL_SHADER_BIT = new VkPipelineStageFlagBits(16);
    public static final VkPipelineStageFlagBits VK_PIPELINE_STAGE_TESSELLATION_EVALUATION_SHADER_BIT = new VkPipelineStageFlagBits(32);
    public static final VkPipelineStageFlagBits VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT = new VkPipelineStageFlagBits(1);
    public static final VkPipelineStageFlagBits VK_PIPELINE_STAGE_TRANSFER_BIT = new VkPipelineStageFlagBits(4096);
    public static final VkPipelineStageFlagBits VK_PIPELINE_STAGE_TRANSFORM_FEEDBACK_BIT_EXT = new VkPipelineStageFlagBits(16777216);
    public static final VkPipelineStageFlagBits VK_PIPELINE_STAGE_VERTEX_INPUT_BIT = new VkPipelineStageFlagBits(4);
    public static final VkPipelineStageFlagBits VK_PIPELINE_STAGE_VERTEX_SHADER_BIT = new VkPipelineStageFlagBits(8);

    public static VkPipelineStageFlagBits fromValue(int value) {
        return switch (value) {
            case 33554432 -> VK_PIPELINE_STAGE_ACCELERATION_STRUCTURE_BUILD_BIT_NV;
            case 65536 -> VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
            case 32768 -> VK_PIPELINE_STAGE_ALL_GRAPHICS_BIT;
            case 8192 -> VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT;
            case 1024 -> VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
            case 131072 -> VK_PIPELINE_STAGE_COMMAND_PREPROCESS_BIT_NV;
            case 2048 -> VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT;
            case 262144 -> VK_PIPELINE_STAGE_CONDITIONAL_RENDERING_BIT_EXT;
            case 2 -> VK_PIPELINE_STAGE_DRAW_INDIRECT_BIT;
            case 256 -> VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT;
            case 2147483647 -> VK_PIPELINE_STAGE_FLAG_BITS_MAX_ENUM;
            case 8388608 -> VK_PIPELINE_STAGE_FRAGMENT_DENSITY_PROCESS_BIT_EXT;
            case 128 -> VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
            case 4194304 -> VK_PIPELINE_STAGE_SHADING_RATE_IMAGE_BIT_NV;
            case 64 -> VK_PIPELINE_STAGE_GEOMETRY_SHADER_BIT;
            case 16384 -> VK_PIPELINE_STAGE_HOST_BIT;
            case 512 -> VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT;
            case 1048576 -> VK_PIPELINE_STAGE_MESH_SHADER_BIT_NV;
            case 0 -> VK_PIPELINE_STAGE_NONE;
            case 2097152 -> VK_PIPELINE_STAGE_RAY_TRACING_SHADER_BIT_NV;
            case 524288 -> VK_PIPELINE_STAGE_TASK_SHADER_BIT_NV;
            case 16 -> VK_PIPELINE_STAGE_TESSELLATION_CONTROL_SHADER_BIT;
            case 32 -> VK_PIPELINE_STAGE_TESSELLATION_EVALUATION_SHADER_BIT;
            case 1 -> VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
            case 4096 -> VK_PIPELINE_STAGE_TRANSFER_BIT;
            case 16777216 -> VK_PIPELINE_STAGE_TRANSFORM_FEEDBACK_BIT_EXT;
            case 4 -> VK_PIPELINE_STAGE_VERTEX_INPUT_BIT;
            case 8 -> VK_PIPELINE_STAGE_VERTEX_SHADER_BIT;
            default -> new VkPipelineStageFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
