package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkOpticalFlowSessionBindingPointNV
 * Generated from jextract bindings
 */
public record VkOpticalFlowSessionBindingPointNV(int value) {

    public static final VkOpticalFlowSessionBindingPointNV VK_OPTICAL_FLOW_SESSION_BINDING_POINT_BACKWARD_COST_NV = new VkOpticalFlowSessionBindingPointNV(7);
    public static final VkOpticalFlowSessionBindingPointNV VK_OPTICAL_FLOW_SESSION_BINDING_POINT_BACKWARD_FLOW_VECTOR_NV = new VkOpticalFlowSessionBindingPointNV(5);
    public static final VkOpticalFlowSessionBindingPointNV VK_OPTICAL_FLOW_SESSION_BINDING_POINT_COST_NV = new VkOpticalFlowSessionBindingPointNV(6);
    public static final VkOpticalFlowSessionBindingPointNV VK_OPTICAL_FLOW_SESSION_BINDING_POINT_FLOW_VECTOR_NV = new VkOpticalFlowSessionBindingPointNV(4);
    public static final VkOpticalFlowSessionBindingPointNV VK_OPTICAL_FLOW_SESSION_BINDING_POINT_GLOBAL_FLOW_NV = new VkOpticalFlowSessionBindingPointNV(8);
    public static final VkOpticalFlowSessionBindingPointNV VK_OPTICAL_FLOW_SESSION_BINDING_POINT_HINT_NV = new VkOpticalFlowSessionBindingPointNV(3);
    public static final VkOpticalFlowSessionBindingPointNV VK_OPTICAL_FLOW_SESSION_BINDING_POINT_INPUT_NV = new VkOpticalFlowSessionBindingPointNV(1);
    public static final VkOpticalFlowSessionBindingPointNV VK_OPTICAL_FLOW_SESSION_BINDING_POINT_MAX_ENUM_NV = new VkOpticalFlowSessionBindingPointNV(2147483647);
    public static final VkOpticalFlowSessionBindingPointNV VK_OPTICAL_FLOW_SESSION_BINDING_POINT_REFERENCE_NV = new VkOpticalFlowSessionBindingPointNV(2);
    public static final VkOpticalFlowSessionBindingPointNV VK_OPTICAL_FLOW_SESSION_BINDING_POINT_UNKNOWN_NV = new VkOpticalFlowSessionBindingPointNV(0);

    public static VkOpticalFlowSessionBindingPointNV fromValue(int value) {
        return switch (value) {
            case 7 -> VK_OPTICAL_FLOW_SESSION_BINDING_POINT_BACKWARD_COST_NV;
            case 5 -> VK_OPTICAL_FLOW_SESSION_BINDING_POINT_BACKWARD_FLOW_VECTOR_NV;
            case 6 -> VK_OPTICAL_FLOW_SESSION_BINDING_POINT_COST_NV;
            case 4 -> VK_OPTICAL_FLOW_SESSION_BINDING_POINT_FLOW_VECTOR_NV;
            case 8 -> VK_OPTICAL_FLOW_SESSION_BINDING_POINT_GLOBAL_FLOW_NV;
            case 3 -> VK_OPTICAL_FLOW_SESSION_BINDING_POINT_HINT_NV;
            case 1 -> VK_OPTICAL_FLOW_SESSION_BINDING_POINT_INPUT_NV;
            case 2147483647 -> VK_OPTICAL_FLOW_SESSION_BINDING_POINT_MAX_ENUM_NV;
            case 2 -> VK_OPTICAL_FLOW_SESSION_BINDING_POINT_REFERENCE_NV;
            case 0 -> VK_OPTICAL_FLOW_SESSION_BINDING_POINT_UNKNOWN_NV;
            default -> new VkOpticalFlowSessionBindingPointNV(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
