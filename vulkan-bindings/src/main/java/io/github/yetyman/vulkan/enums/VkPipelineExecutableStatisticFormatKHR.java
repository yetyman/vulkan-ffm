package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkPipelineExecutableStatisticFormatKHR
 * Generated from jextract bindings
 */
public record VkPipelineExecutableStatisticFormatKHR(int value) {

    public static final VkPipelineExecutableStatisticFormatKHR VK_PIPELINE_EXECUTABLE_STATISTIC_FORMAT_BOOL32_KHR = new VkPipelineExecutableStatisticFormatKHR(0);
    public static final VkPipelineExecutableStatisticFormatKHR VK_PIPELINE_EXECUTABLE_STATISTIC_FORMAT_FLOAT64_KHR = new VkPipelineExecutableStatisticFormatKHR(3);
    public static final VkPipelineExecutableStatisticFormatKHR VK_PIPELINE_EXECUTABLE_STATISTIC_FORMAT_INT64_KHR = new VkPipelineExecutableStatisticFormatKHR(1);
    public static final VkPipelineExecutableStatisticFormatKHR VK_PIPELINE_EXECUTABLE_STATISTIC_FORMAT_MAX_ENUM_KHR = new VkPipelineExecutableStatisticFormatKHR(2147483647);
    public static final VkPipelineExecutableStatisticFormatKHR VK_PIPELINE_EXECUTABLE_STATISTIC_FORMAT_UINT64_KHR = new VkPipelineExecutableStatisticFormatKHR(2);

    public static VkPipelineExecutableStatisticFormatKHR fromValue(int value) {
        return switch (value) {
            case 0 -> VK_PIPELINE_EXECUTABLE_STATISTIC_FORMAT_BOOL32_KHR;
            case 3 -> VK_PIPELINE_EXECUTABLE_STATISTIC_FORMAT_FLOAT64_KHR;
            case 1 -> VK_PIPELINE_EXECUTABLE_STATISTIC_FORMAT_INT64_KHR;
            case 2147483647 -> VK_PIPELINE_EXECUTABLE_STATISTIC_FORMAT_MAX_ENUM_KHR;
            case 2 -> VK_PIPELINE_EXECUTABLE_STATISTIC_FORMAT_UINT64_KHR;
            default -> new VkPipelineExecutableStatisticFormatKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
