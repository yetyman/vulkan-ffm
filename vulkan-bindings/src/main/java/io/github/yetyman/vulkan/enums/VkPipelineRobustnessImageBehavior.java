package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkPipelineRobustnessImageBehavior
 * Generated from jextract bindings
 */
public record VkPipelineRobustnessImageBehavior(int value) {

    public static final VkPipelineRobustnessImageBehavior VK_PIPELINE_ROBUSTNESS_IMAGE_BEHAVIOR_DEVICE_DEFAULT = new VkPipelineRobustnessImageBehavior(0);
    public static final VkPipelineRobustnessImageBehavior VK_PIPELINE_ROBUSTNESS_IMAGE_BEHAVIOR_DEVICE_DEFAULT_EXT = VK_PIPELINE_ROBUSTNESS_IMAGE_BEHAVIOR_DEVICE_DEFAULT;
    public static final VkPipelineRobustnessImageBehavior VK_PIPELINE_ROBUSTNESS_IMAGE_BEHAVIOR_DISABLED = new VkPipelineRobustnessImageBehavior(1);
    public static final VkPipelineRobustnessImageBehavior VK_PIPELINE_ROBUSTNESS_IMAGE_BEHAVIOR_DISABLED_EXT = VK_PIPELINE_ROBUSTNESS_IMAGE_BEHAVIOR_DISABLED;
    public static final VkPipelineRobustnessImageBehavior VK_PIPELINE_ROBUSTNESS_IMAGE_BEHAVIOR_MAX_ENUM = new VkPipelineRobustnessImageBehavior(2147483647);
    public static final VkPipelineRobustnessImageBehavior VK_PIPELINE_ROBUSTNESS_IMAGE_BEHAVIOR_ROBUST_IMAGE_ACCESS = new VkPipelineRobustnessImageBehavior(2);
    public static final VkPipelineRobustnessImageBehavior VK_PIPELINE_ROBUSTNESS_IMAGE_BEHAVIOR_ROBUST_IMAGE_ACCESS_2 = new VkPipelineRobustnessImageBehavior(3);
    public static final VkPipelineRobustnessImageBehavior VK_PIPELINE_ROBUSTNESS_IMAGE_BEHAVIOR_ROBUST_IMAGE_ACCESS_2_EXT = VK_PIPELINE_ROBUSTNESS_IMAGE_BEHAVIOR_ROBUST_IMAGE_ACCESS_2;
    public static final VkPipelineRobustnessImageBehavior VK_PIPELINE_ROBUSTNESS_IMAGE_BEHAVIOR_ROBUST_IMAGE_ACCESS_EXT = VK_PIPELINE_ROBUSTNESS_IMAGE_BEHAVIOR_ROBUST_IMAGE_ACCESS;

    public static VkPipelineRobustnessImageBehavior fromValue(int value) {
        return switch (value) {
            case 0 -> VK_PIPELINE_ROBUSTNESS_IMAGE_BEHAVIOR_DEVICE_DEFAULT;
            case 1 -> VK_PIPELINE_ROBUSTNESS_IMAGE_BEHAVIOR_DISABLED;
            case 2147483647 -> VK_PIPELINE_ROBUSTNESS_IMAGE_BEHAVIOR_MAX_ENUM;
            case 2 -> VK_PIPELINE_ROBUSTNESS_IMAGE_BEHAVIOR_ROBUST_IMAGE_ACCESS;
            case 3 -> VK_PIPELINE_ROBUSTNESS_IMAGE_BEHAVIOR_ROBUST_IMAGE_ACCESS_2;
            default -> new VkPipelineRobustnessImageBehavior(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
