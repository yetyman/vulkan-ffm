package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkAntiLagModeAMD
 * Generated from jextract bindings
 */
public record VkAntiLagModeAMD(int value) {

    public static final VkAntiLagModeAMD VK_ANTI_LAG_MODE_DRIVER_CONTROL_AMD = new VkAntiLagModeAMD(0);
    public static final VkAntiLagModeAMD VK_ANTI_LAG_MODE_MAX_ENUM_AMD = new VkAntiLagModeAMD(2147483647);
    public static final VkAntiLagModeAMD VK_ANTI_LAG_MODE_OFF_AMD = new VkAntiLagModeAMD(2);
    public static final VkAntiLagModeAMD VK_ANTI_LAG_MODE_ON_AMD = new VkAntiLagModeAMD(1);

    public static VkAntiLagModeAMD fromValue(int value) {
        return switch (value) {
            case 0 -> VK_ANTI_LAG_MODE_DRIVER_CONTROL_AMD;
            case 2147483647 -> VK_ANTI_LAG_MODE_MAX_ENUM_AMD;
            case 2 -> VK_ANTI_LAG_MODE_OFF_AMD;
            case 1 -> VK_ANTI_LAG_MODE_ON_AMD;
            default -> new VkAntiLagModeAMD(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
