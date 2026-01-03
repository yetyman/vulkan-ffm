package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkOpticalFlowSessionCreateFlagBitsNV
 * Generated from jextract bindings
 */
public record VkOpticalFlowSessionCreateFlagBitsNV(int value) {

    public static final VkOpticalFlowSessionCreateFlagBitsNV VK_OPTICAL_FLOW_SESSION_CREATE_ALLOW_REGIONS_BIT_NV = new VkOpticalFlowSessionCreateFlagBitsNV(8);
    public static final VkOpticalFlowSessionCreateFlagBitsNV VK_OPTICAL_FLOW_SESSION_CREATE_BOTH_DIRECTIONS_BIT_NV = new VkOpticalFlowSessionCreateFlagBitsNV(16);
    public static final VkOpticalFlowSessionCreateFlagBitsNV VK_OPTICAL_FLOW_SESSION_CREATE_ENABLE_COST_BIT_NV = new VkOpticalFlowSessionCreateFlagBitsNV(2);
    public static final VkOpticalFlowSessionCreateFlagBitsNV VK_OPTICAL_FLOW_SESSION_CREATE_ENABLE_GLOBAL_FLOW_BIT_NV = new VkOpticalFlowSessionCreateFlagBitsNV(4);
    public static final VkOpticalFlowSessionCreateFlagBitsNV VK_OPTICAL_FLOW_SESSION_CREATE_ENABLE_HINT_BIT_NV = new VkOpticalFlowSessionCreateFlagBitsNV(1);
    public static final VkOpticalFlowSessionCreateFlagBitsNV VK_OPTICAL_FLOW_SESSION_CREATE_FLAG_BITS_MAX_ENUM_NV = new VkOpticalFlowSessionCreateFlagBitsNV(2147483647);

    public static VkOpticalFlowSessionCreateFlagBitsNV fromValue(int value) {
        return switch (value) {
            case 8 -> VK_OPTICAL_FLOW_SESSION_CREATE_ALLOW_REGIONS_BIT_NV;
            case 16 -> VK_OPTICAL_FLOW_SESSION_CREATE_BOTH_DIRECTIONS_BIT_NV;
            case 2 -> VK_OPTICAL_FLOW_SESSION_CREATE_ENABLE_COST_BIT_NV;
            case 4 -> VK_OPTICAL_FLOW_SESSION_CREATE_ENABLE_GLOBAL_FLOW_BIT_NV;
            case 1 -> VK_OPTICAL_FLOW_SESSION_CREATE_ENABLE_HINT_BIT_NV;
            case 2147483647 -> VK_OPTICAL_FLOW_SESSION_CREATE_FLAG_BITS_MAX_ENUM_NV;
            default -> new VkOpticalFlowSessionCreateFlagBitsNV(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
