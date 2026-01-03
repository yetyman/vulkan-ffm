package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkOpticalFlowGridSizeFlagBitsNV
 * Generated from jextract bindings
 */
public record VkOpticalFlowGridSizeFlagBitsNV(int value) {

    public static final VkOpticalFlowGridSizeFlagBitsNV VK_OPTICAL_FLOW_GRID_SIZE_1X1_BIT_NV = new VkOpticalFlowGridSizeFlagBitsNV(1);
    public static final VkOpticalFlowGridSizeFlagBitsNV VK_OPTICAL_FLOW_GRID_SIZE_2X2_BIT_NV = new VkOpticalFlowGridSizeFlagBitsNV(2);
    public static final VkOpticalFlowGridSizeFlagBitsNV VK_OPTICAL_FLOW_GRID_SIZE_4X4_BIT_NV = new VkOpticalFlowGridSizeFlagBitsNV(4);
    public static final VkOpticalFlowGridSizeFlagBitsNV VK_OPTICAL_FLOW_GRID_SIZE_8X8_BIT_NV = new VkOpticalFlowGridSizeFlagBitsNV(8);
    public static final VkOpticalFlowGridSizeFlagBitsNV VK_OPTICAL_FLOW_GRID_SIZE_FLAG_BITS_MAX_ENUM_NV = new VkOpticalFlowGridSizeFlagBitsNV(2147483647);
    public static final VkOpticalFlowGridSizeFlagBitsNV VK_OPTICAL_FLOW_GRID_SIZE_UNKNOWN_NV = new VkOpticalFlowGridSizeFlagBitsNV(0);

    public static VkOpticalFlowGridSizeFlagBitsNV fromValue(int value) {
        return switch (value) {
            case 1 -> VK_OPTICAL_FLOW_GRID_SIZE_1X1_BIT_NV;
            case 2 -> VK_OPTICAL_FLOW_GRID_SIZE_2X2_BIT_NV;
            case 4 -> VK_OPTICAL_FLOW_GRID_SIZE_4X4_BIT_NV;
            case 8 -> VK_OPTICAL_FLOW_GRID_SIZE_8X8_BIT_NV;
            case 2147483647 -> VK_OPTICAL_FLOW_GRID_SIZE_FLAG_BITS_MAX_ENUM_NV;
            case 0 -> VK_OPTICAL_FLOW_GRID_SIZE_UNKNOWN_NV;
            default -> new VkOpticalFlowGridSizeFlagBitsNV(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
