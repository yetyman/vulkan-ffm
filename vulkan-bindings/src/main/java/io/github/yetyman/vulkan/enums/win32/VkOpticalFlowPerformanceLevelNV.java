package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkOpticalFlowPerformanceLevelNV
 * Generated from jextract bindings
 */
public record VkOpticalFlowPerformanceLevelNV(int value) {

    public static final VkOpticalFlowPerformanceLevelNV VK_OPTICAL_FLOW_PERFORMANCE_LEVEL_FAST_NV = new VkOpticalFlowPerformanceLevelNV(3);
    public static final VkOpticalFlowPerformanceLevelNV VK_OPTICAL_FLOW_PERFORMANCE_LEVEL_MAX_ENUM_NV = new VkOpticalFlowPerformanceLevelNV(2147483647);
    public static final VkOpticalFlowPerformanceLevelNV VK_OPTICAL_FLOW_PERFORMANCE_LEVEL_MEDIUM_NV = new VkOpticalFlowPerformanceLevelNV(2);
    public static final VkOpticalFlowPerformanceLevelNV VK_OPTICAL_FLOW_PERFORMANCE_LEVEL_SLOW_NV = new VkOpticalFlowPerformanceLevelNV(1);
    public static final VkOpticalFlowPerformanceLevelNV VK_OPTICAL_FLOW_PERFORMANCE_LEVEL_UNKNOWN_NV = new VkOpticalFlowPerformanceLevelNV(0);

    public static VkOpticalFlowPerformanceLevelNV fromValue(int value) {
        return switch (value) {
            case 3 -> VK_OPTICAL_FLOW_PERFORMANCE_LEVEL_FAST_NV;
            case 2147483647 -> VK_OPTICAL_FLOW_PERFORMANCE_LEVEL_MAX_ENUM_NV;
            case 2 -> VK_OPTICAL_FLOW_PERFORMANCE_LEVEL_MEDIUM_NV;
            case 1 -> VK_OPTICAL_FLOW_PERFORMANCE_LEVEL_SLOW_NV;
            case 0 -> VK_OPTICAL_FLOW_PERFORMANCE_LEVEL_UNKNOWN_NV;
            default -> new VkOpticalFlowPerformanceLevelNV(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
