package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkOpticalFlowExecuteFlagBitsNV
 * Generated from jextract bindings
 */
public record VkOpticalFlowExecuteFlagBitsNV(int value) {

    public static final VkOpticalFlowExecuteFlagBitsNV VK_OPTICAL_FLOW_EXECUTE_DISABLE_TEMPORAL_HINTS_BIT_NV = new VkOpticalFlowExecuteFlagBitsNV(1);
    public static final VkOpticalFlowExecuteFlagBitsNV VK_OPTICAL_FLOW_EXECUTE_FLAG_BITS_MAX_ENUM_NV = new VkOpticalFlowExecuteFlagBitsNV(2147483647);

    public static VkOpticalFlowExecuteFlagBitsNV fromValue(int value) {
        return switch (value) {
            case 1 -> VK_OPTICAL_FLOW_EXECUTE_DISABLE_TEMPORAL_HINTS_BIT_NV;
            case 2147483647 -> VK_OPTICAL_FLOW_EXECUTE_FLAG_BITS_MAX_ENUM_NV;
            default -> new VkOpticalFlowExecuteFlagBitsNV(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
