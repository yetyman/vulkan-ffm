package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkOpticalFlowUsageFlagBitsNV
 * Generated from jextract bindings
 */
public record VkOpticalFlowUsageFlagBitsNV(int value) {

    public static final VkOpticalFlowUsageFlagBitsNV VK_OPTICAL_FLOW_USAGE_COST_BIT_NV = new VkOpticalFlowUsageFlagBitsNV(8);
    public static final VkOpticalFlowUsageFlagBitsNV VK_OPTICAL_FLOW_USAGE_FLAG_BITS_MAX_ENUM_NV = new VkOpticalFlowUsageFlagBitsNV(2147483647);
    public static final VkOpticalFlowUsageFlagBitsNV VK_OPTICAL_FLOW_USAGE_GLOBAL_FLOW_BIT_NV = new VkOpticalFlowUsageFlagBitsNV(16);
    public static final VkOpticalFlowUsageFlagBitsNV VK_OPTICAL_FLOW_USAGE_HINT_BIT_NV = new VkOpticalFlowUsageFlagBitsNV(4);
    public static final VkOpticalFlowUsageFlagBitsNV VK_OPTICAL_FLOW_USAGE_INPUT_BIT_NV = new VkOpticalFlowUsageFlagBitsNV(1);
    public static final VkOpticalFlowUsageFlagBitsNV VK_OPTICAL_FLOW_USAGE_OUTPUT_BIT_NV = new VkOpticalFlowUsageFlagBitsNV(2);
    public static final VkOpticalFlowUsageFlagBitsNV VK_OPTICAL_FLOW_USAGE_UNKNOWN_NV = new VkOpticalFlowUsageFlagBitsNV(0);

    public static VkOpticalFlowUsageFlagBitsNV fromValue(int value) {
        return switch (value) {
            case 8 -> VK_OPTICAL_FLOW_USAGE_COST_BIT_NV;
            case 2147483647 -> VK_OPTICAL_FLOW_USAGE_FLAG_BITS_MAX_ENUM_NV;
            case 16 -> VK_OPTICAL_FLOW_USAGE_GLOBAL_FLOW_BIT_NV;
            case 4 -> VK_OPTICAL_FLOW_USAGE_HINT_BIT_NV;
            case 1 -> VK_OPTICAL_FLOW_USAGE_INPUT_BIT_NV;
            case 2 -> VK_OPTICAL_FLOW_USAGE_OUTPUT_BIT_NV;
            case 0 -> VK_OPTICAL_FLOW_USAGE_UNKNOWN_NV;
            default -> new VkOpticalFlowUsageFlagBitsNV(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
