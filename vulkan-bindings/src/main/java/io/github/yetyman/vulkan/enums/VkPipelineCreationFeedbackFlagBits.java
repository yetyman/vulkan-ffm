package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkPipelineCreationFeedbackFlagBits
 * Generated from jextract bindings
 */
public record VkPipelineCreationFeedbackFlagBits(int value) {

    public static final VkPipelineCreationFeedbackFlagBits VK_PIPELINE_CREATION_FEEDBACK_APPLICATION_PIPELINE_CACHE_HIT_BIT = new VkPipelineCreationFeedbackFlagBits(2);
    public static final VkPipelineCreationFeedbackFlagBits VK_PIPELINE_CREATION_FEEDBACK_APPLICATION_PIPELINE_CACHE_HIT_BIT_EXT = VK_PIPELINE_CREATION_FEEDBACK_APPLICATION_PIPELINE_CACHE_HIT_BIT;
    public static final VkPipelineCreationFeedbackFlagBits VK_PIPELINE_CREATION_FEEDBACK_BASE_PIPELINE_ACCELERATION_BIT = new VkPipelineCreationFeedbackFlagBits(4);
    public static final VkPipelineCreationFeedbackFlagBits VK_PIPELINE_CREATION_FEEDBACK_BASE_PIPELINE_ACCELERATION_BIT_EXT = VK_PIPELINE_CREATION_FEEDBACK_BASE_PIPELINE_ACCELERATION_BIT;
    public static final VkPipelineCreationFeedbackFlagBits VK_PIPELINE_CREATION_FEEDBACK_FLAG_BITS_MAX_ENUM = new VkPipelineCreationFeedbackFlagBits(2147483647);
    public static final VkPipelineCreationFeedbackFlagBits VK_PIPELINE_CREATION_FEEDBACK_VALID_BIT = new VkPipelineCreationFeedbackFlagBits(1);
    public static final VkPipelineCreationFeedbackFlagBits VK_PIPELINE_CREATION_FEEDBACK_VALID_BIT_EXT = VK_PIPELINE_CREATION_FEEDBACK_VALID_BIT;

    public static VkPipelineCreationFeedbackFlagBits fromValue(int value) {
        return switch (value) {
            case 2 -> VK_PIPELINE_CREATION_FEEDBACK_APPLICATION_PIPELINE_CACHE_HIT_BIT;
            case 4 -> VK_PIPELINE_CREATION_FEEDBACK_BASE_PIPELINE_ACCELERATION_BIT;
            case 2147483647 -> VK_PIPELINE_CREATION_FEEDBACK_FLAG_BITS_MAX_ENUM;
            case 1 -> VK_PIPELINE_CREATION_FEEDBACK_VALID_BIT;
            default -> new VkPipelineCreationFeedbackFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
