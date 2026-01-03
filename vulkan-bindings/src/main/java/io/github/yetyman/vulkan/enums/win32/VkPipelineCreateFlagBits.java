package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkPipelineCreateFlagBits
 * Generated from jextract bindings
 */
public record VkPipelineCreateFlagBits(int value) {

    public static final VkPipelineCreateFlagBits VK_PIPELINE_CREATE_ALLOW_DERIVATIVES_BIT = new VkPipelineCreateFlagBits(2);
    public static final VkPipelineCreateFlagBits VK_PIPELINE_CREATE_CAPTURE_INTERNAL_REPRESENTATIONS_BIT_KHR = new VkPipelineCreateFlagBits(128);
    public static final VkPipelineCreateFlagBits VK_PIPELINE_CREATE_CAPTURE_STATISTICS_BIT_KHR = new VkPipelineCreateFlagBits(64);
    public static final VkPipelineCreateFlagBits VK_PIPELINE_CREATE_COLOR_ATTACHMENT_FEEDBACK_LOOP_BIT_EXT = new VkPipelineCreateFlagBits(33554432);
    public static final VkPipelineCreateFlagBits VK_PIPELINE_CREATE_DEFER_COMPILE_BIT_NV = new VkPipelineCreateFlagBits(32);
    public static final VkPipelineCreateFlagBits VK_PIPELINE_CREATE_DEPTH_STENCIL_ATTACHMENT_FEEDBACK_LOOP_BIT_EXT = new VkPipelineCreateFlagBits(67108864);
    public static final VkPipelineCreateFlagBits VK_PIPELINE_CREATE_DERIVATIVE_BIT = new VkPipelineCreateFlagBits(4);
    public static final VkPipelineCreateFlagBits VK_PIPELINE_CREATE_DESCRIPTOR_BUFFER_BIT_EXT = new VkPipelineCreateFlagBits(536870912);
    public static final VkPipelineCreateFlagBits VK_PIPELINE_CREATE_DISABLE_OPTIMIZATION_BIT = new VkPipelineCreateFlagBits(1);
    public static final VkPipelineCreateFlagBits VK_PIPELINE_CREATE_DISPATCH_BASE = new VkPipelineCreateFlagBits(16);
    public static final VkPipelineCreateFlagBits VK_PIPELINE_CREATE_DISPATCH_BASE_BIT = new VkPipelineCreateFlagBits(16);
    public static final VkPipelineCreateFlagBits VK_PIPELINE_CREATE_DISPATCH_BASE_BIT_KHR = new VkPipelineCreateFlagBits(16);
    public static final VkPipelineCreateFlagBits VK_PIPELINE_CREATE_DISPATCH_BASE_KHR = new VkPipelineCreateFlagBits(16);
    public static final VkPipelineCreateFlagBits VK_PIPELINE_CREATE_EARLY_RETURN_ON_FAILURE_BIT = new VkPipelineCreateFlagBits(512);
    public static final VkPipelineCreateFlagBits VK_PIPELINE_CREATE_EARLY_RETURN_ON_FAILURE_BIT_EXT = new VkPipelineCreateFlagBits(512);
    public static final VkPipelineCreateFlagBits VK_PIPELINE_CREATE_FAIL_ON_PIPELINE_COMPILE_REQUIRED_BIT = new VkPipelineCreateFlagBits(256);
    public static final VkPipelineCreateFlagBits VK_PIPELINE_CREATE_FAIL_ON_PIPELINE_COMPILE_REQUIRED_BIT_EXT = new VkPipelineCreateFlagBits(256);
    public static final VkPipelineCreateFlagBits VK_PIPELINE_CREATE_FLAG_BITS_MAX_ENUM = new VkPipelineCreateFlagBits(2147483647);
    public static final VkPipelineCreateFlagBits VK_PIPELINE_CREATE_INDIRECT_BINDABLE_BIT_NV = new VkPipelineCreateFlagBits(262144);
    public static final VkPipelineCreateFlagBits VK_PIPELINE_CREATE_LIBRARY_BIT_KHR = new VkPipelineCreateFlagBits(2048);
    public static final VkPipelineCreateFlagBits VK_PIPELINE_CREATE_LINK_TIME_OPTIMIZATION_BIT_EXT = new VkPipelineCreateFlagBits(1024);
    public static final VkPipelineCreateFlagBits VK_PIPELINE_CREATE_NO_PROTECTED_ACCESS_BIT = new VkPipelineCreateFlagBits(134217728);
    public static final VkPipelineCreateFlagBits VK_PIPELINE_CREATE_NO_PROTECTED_ACCESS_BIT_EXT = new VkPipelineCreateFlagBits(134217728);
    public static final VkPipelineCreateFlagBits VK_PIPELINE_CREATE_PROTECTED_ACCESS_ONLY_BIT = new VkPipelineCreateFlagBits(1073741824);
    public static final VkPipelineCreateFlagBits VK_PIPELINE_CREATE_PROTECTED_ACCESS_ONLY_BIT_EXT = new VkPipelineCreateFlagBits(1073741824);
    public static final VkPipelineCreateFlagBits VK_PIPELINE_CREATE_RAY_TRACING_ALLOW_MOTION_BIT_NV = new VkPipelineCreateFlagBits(1048576);
    public static final VkPipelineCreateFlagBits VK_PIPELINE_CREATE_RAY_TRACING_NO_NULL_ANY_HIT_SHADERS_BIT_KHR = new VkPipelineCreateFlagBits(16384);
    public static final VkPipelineCreateFlagBits VK_PIPELINE_CREATE_RAY_TRACING_NO_NULL_CLOSEST_HIT_SHADERS_BIT_KHR = new VkPipelineCreateFlagBits(32768);
    public static final VkPipelineCreateFlagBits VK_PIPELINE_CREATE_RAY_TRACING_NO_NULL_INTERSECTION_SHADERS_BIT_KHR = new VkPipelineCreateFlagBits(131072);
    public static final VkPipelineCreateFlagBits VK_PIPELINE_CREATE_RAY_TRACING_NO_NULL_MISS_SHADERS_BIT_KHR = new VkPipelineCreateFlagBits(65536);
    public static final VkPipelineCreateFlagBits VK_PIPELINE_CREATE_RAY_TRACING_OPACITY_MICROMAP_BIT_EXT = new VkPipelineCreateFlagBits(16777216);
    public static final VkPipelineCreateFlagBits VK_PIPELINE_CREATE_RAY_TRACING_SHADER_GROUP_HANDLE_CAPTURE_REPLAY_BIT_KHR = new VkPipelineCreateFlagBits(524288);
    public static final VkPipelineCreateFlagBits VK_PIPELINE_CREATE_RAY_TRACING_SKIP_AABBS_BIT_KHR = new VkPipelineCreateFlagBits(8192);
    public static final VkPipelineCreateFlagBits VK_PIPELINE_CREATE_RAY_TRACING_SKIP_TRIANGLES_BIT_KHR = new VkPipelineCreateFlagBits(4096);
    public static final VkPipelineCreateFlagBits VK_PIPELINE_CREATE_RENDERING_FRAGMENT_DENSITY_MAP_ATTACHMENT_BIT_EXT = new VkPipelineCreateFlagBits(4194304);
    public static final VkPipelineCreateFlagBits VK_PIPELINE_CREATE_RENDERING_FRAGMENT_SHADING_RATE_ATTACHMENT_BIT_KHR = new VkPipelineCreateFlagBits(2097152);
    public static final VkPipelineCreateFlagBits VK_PIPELINE_CREATE_RETAIN_LINK_TIME_OPTIMIZATION_INFO_BIT_EXT = new VkPipelineCreateFlagBits(8388608);
    public static final VkPipelineCreateFlagBits VK_PIPELINE_CREATE_VIEW_INDEX_FROM_DEVICE_INDEX_BIT = new VkPipelineCreateFlagBits(8);
    public static final VkPipelineCreateFlagBits VK_PIPELINE_CREATE_VIEW_INDEX_FROM_DEVICE_INDEX_BIT_KHR = new VkPipelineCreateFlagBits(8);
    public static final VkPipelineCreateFlagBits VK_PIPELINE_RASTERIZATION_STATE_CREATE_FRAGMENT_DENSITY_MAP_ATTACHMENT_BIT_EXT = new VkPipelineCreateFlagBits(4194304);
    public static final VkPipelineCreateFlagBits VK_PIPELINE_RASTERIZATION_STATE_CREATE_FRAGMENT_SHADING_RATE_ATTACHMENT_BIT_KHR = new VkPipelineCreateFlagBits(2097152);

    public static VkPipelineCreateFlagBits fromValue(int value) {
        return switch (value) {
            case 2 -> VK_PIPELINE_CREATE_ALLOW_DERIVATIVES_BIT;
            case 128 -> VK_PIPELINE_CREATE_CAPTURE_INTERNAL_REPRESENTATIONS_BIT_KHR;
            case 64 -> VK_PIPELINE_CREATE_CAPTURE_STATISTICS_BIT_KHR;
            case 33554432 -> VK_PIPELINE_CREATE_COLOR_ATTACHMENT_FEEDBACK_LOOP_BIT_EXT;
            case 32 -> VK_PIPELINE_CREATE_DEFER_COMPILE_BIT_NV;
            case 67108864 -> VK_PIPELINE_CREATE_DEPTH_STENCIL_ATTACHMENT_FEEDBACK_LOOP_BIT_EXT;
            case 4 -> VK_PIPELINE_CREATE_DERIVATIVE_BIT;
            case 536870912 -> VK_PIPELINE_CREATE_DESCRIPTOR_BUFFER_BIT_EXT;
            case 1 -> VK_PIPELINE_CREATE_DISABLE_OPTIMIZATION_BIT;
            case 16 -> VK_PIPELINE_CREATE_DISPATCH_BASE;
            case 512 -> VK_PIPELINE_CREATE_EARLY_RETURN_ON_FAILURE_BIT;
            case 256 -> VK_PIPELINE_CREATE_FAIL_ON_PIPELINE_COMPILE_REQUIRED_BIT;
            case 2147483647 -> VK_PIPELINE_CREATE_FLAG_BITS_MAX_ENUM;
            case 262144 -> VK_PIPELINE_CREATE_INDIRECT_BINDABLE_BIT_NV;
            case 2048 -> VK_PIPELINE_CREATE_LIBRARY_BIT_KHR;
            case 1024 -> VK_PIPELINE_CREATE_LINK_TIME_OPTIMIZATION_BIT_EXT;
            case 134217728 -> VK_PIPELINE_CREATE_NO_PROTECTED_ACCESS_BIT;
            case 1073741824 -> VK_PIPELINE_CREATE_PROTECTED_ACCESS_ONLY_BIT;
            case 1048576 -> VK_PIPELINE_CREATE_RAY_TRACING_ALLOW_MOTION_BIT_NV;
            case 16384 -> VK_PIPELINE_CREATE_RAY_TRACING_NO_NULL_ANY_HIT_SHADERS_BIT_KHR;
            case 32768 -> VK_PIPELINE_CREATE_RAY_TRACING_NO_NULL_CLOSEST_HIT_SHADERS_BIT_KHR;
            case 131072 -> VK_PIPELINE_CREATE_RAY_TRACING_NO_NULL_INTERSECTION_SHADERS_BIT_KHR;
            case 65536 -> VK_PIPELINE_CREATE_RAY_TRACING_NO_NULL_MISS_SHADERS_BIT_KHR;
            case 16777216 -> VK_PIPELINE_CREATE_RAY_TRACING_OPACITY_MICROMAP_BIT_EXT;
            case 524288 -> VK_PIPELINE_CREATE_RAY_TRACING_SHADER_GROUP_HANDLE_CAPTURE_REPLAY_BIT_KHR;
            case 8192 -> VK_PIPELINE_CREATE_RAY_TRACING_SKIP_AABBS_BIT_KHR;
            case 4096 -> VK_PIPELINE_CREATE_RAY_TRACING_SKIP_TRIANGLES_BIT_KHR;
            case 4194304 -> VK_PIPELINE_CREATE_RENDERING_FRAGMENT_DENSITY_MAP_ATTACHMENT_BIT_EXT;
            case 2097152 -> VK_PIPELINE_CREATE_RENDERING_FRAGMENT_SHADING_RATE_ATTACHMENT_BIT_KHR;
            case 8388608 -> VK_PIPELINE_CREATE_RETAIN_LINK_TIME_OPTIMIZATION_INFO_BIT_EXT;
            case 8 -> VK_PIPELINE_CREATE_VIEW_INDEX_FROM_DEVICE_INDEX_BIT;
            default -> new VkPipelineCreateFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
