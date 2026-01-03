package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkPipelineRobustnessBufferBehavior
 * Generated from jextract bindings
 */
public record VkPipelineRobustnessBufferBehavior(int value) {

    public static final VkPipelineRobustnessBufferBehavior VK_PIPELINE_ROBUSTNESS_BUFFER_BEHAVIOR_DEVICE_DEFAULT = new VkPipelineRobustnessBufferBehavior(0);
    public static final VkPipelineRobustnessBufferBehavior VK_PIPELINE_ROBUSTNESS_BUFFER_BEHAVIOR_DEVICE_DEFAULT_EXT = VK_PIPELINE_ROBUSTNESS_BUFFER_BEHAVIOR_DEVICE_DEFAULT;
    public static final VkPipelineRobustnessBufferBehavior VK_PIPELINE_ROBUSTNESS_BUFFER_BEHAVIOR_DISABLED = new VkPipelineRobustnessBufferBehavior(1);
    public static final VkPipelineRobustnessBufferBehavior VK_PIPELINE_ROBUSTNESS_BUFFER_BEHAVIOR_DISABLED_EXT = VK_PIPELINE_ROBUSTNESS_BUFFER_BEHAVIOR_DISABLED;
    public static final VkPipelineRobustnessBufferBehavior VK_PIPELINE_ROBUSTNESS_BUFFER_BEHAVIOR_MAX_ENUM = new VkPipelineRobustnessBufferBehavior(2147483647);
    public static final VkPipelineRobustnessBufferBehavior VK_PIPELINE_ROBUSTNESS_BUFFER_BEHAVIOR_ROBUST_BUFFER_ACCESS = new VkPipelineRobustnessBufferBehavior(2);
    public static final VkPipelineRobustnessBufferBehavior VK_PIPELINE_ROBUSTNESS_BUFFER_BEHAVIOR_ROBUST_BUFFER_ACCESS_2 = new VkPipelineRobustnessBufferBehavior(3);
    public static final VkPipelineRobustnessBufferBehavior VK_PIPELINE_ROBUSTNESS_BUFFER_BEHAVIOR_ROBUST_BUFFER_ACCESS_2_EXT = VK_PIPELINE_ROBUSTNESS_BUFFER_BEHAVIOR_ROBUST_BUFFER_ACCESS_2;
    public static final VkPipelineRobustnessBufferBehavior VK_PIPELINE_ROBUSTNESS_BUFFER_BEHAVIOR_ROBUST_BUFFER_ACCESS_EXT = VK_PIPELINE_ROBUSTNESS_BUFFER_BEHAVIOR_ROBUST_BUFFER_ACCESS;

    public static VkPipelineRobustnessBufferBehavior fromValue(int value) {
        return switch (value) {
            case 0 -> VK_PIPELINE_ROBUSTNESS_BUFFER_BEHAVIOR_DEVICE_DEFAULT;
            case 1 -> VK_PIPELINE_ROBUSTNESS_BUFFER_BEHAVIOR_DISABLED;
            case 2147483647 -> VK_PIPELINE_ROBUSTNESS_BUFFER_BEHAVIOR_MAX_ENUM;
            case 2 -> VK_PIPELINE_ROBUSTNESS_BUFFER_BEHAVIOR_ROBUST_BUFFER_ACCESS;
            case 3 -> VK_PIPELINE_ROBUSTNESS_BUFFER_BEHAVIOR_ROBUST_BUFFER_ACCESS_2;
            default -> new VkPipelineRobustnessBufferBehavior(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
