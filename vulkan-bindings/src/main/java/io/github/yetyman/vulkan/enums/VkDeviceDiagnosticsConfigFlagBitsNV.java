package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkDeviceDiagnosticsConfigFlagBitsNV
 * Generated from jextract bindings
 */
public record VkDeviceDiagnosticsConfigFlagBitsNV(int value) {

    public static final VkDeviceDiagnosticsConfigFlagBitsNV VK_DEVICE_DIAGNOSTICS_CONFIG_ENABLE_AUTOMATIC_CHECKPOINTS_BIT_NV = new VkDeviceDiagnosticsConfigFlagBitsNV(4);
    public static final VkDeviceDiagnosticsConfigFlagBitsNV VK_DEVICE_DIAGNOSTICS_CONFIG_ENABLE_RESOURCE_TRACKING_BIT_NV = new VkDeviceDiagnosticsConfigFlagBitsNV(2);
    public static final VkDeviceDiagnosticsConfigFlagBitsNV VK_DEVICE_DIAGNOSTICS_CONFIG_ENABLE_SHADER_DEBUG_INFO_BIT_NV = new VkDeviceDiagnosticsConfigFlagBitsNV(1);
    public static final VkDeviceDiagnosticsConfigFlagBitsNV VK_DEVICE_DIAGNOSTICS_CONFIG_ENABLE_SHADER_ERROR_REPORTING_BIT_NV = new VkDeviceDiagnosticsConfigFlagBitsNV(8);
    public static final VkDeviceDiagnosticsConfigFlagBitsNV VK_DEVICE_DIAGNOSTICS_CONFIG_FLAG_BITS_MAX_ENUM_NV = new VkDeviceDiagnosticsConfigFlagBitsNV(2147483647);

    public static VkDeviceDiagnosticsConfigFlagBitsNV fromValue(int value) {
        return switch (value) {
            case 4 -> VK_DEVICE_DIAGNOSTICS_CONFIG_ENABLE_AUTOMATIC_CHECKPOINTS_BIT_NV;
            case 2 -> VK_DEVICE_DIAGNOSTICS_CONFIG_ENABLE_RESOURCE_TRACKING_BIT_NV;
            case 1 -> VK_DEVICE_DIAGNOSTICS_CONFIG_ENABLE_SHADER_DEBUG_INFO_BIT_NV;
            case 8 -> VK_DEVICE_DIAGNOSTICS_CONFIG_ENABLE_SHADER_ERROR_REPORTING_BIT_NV;
            case 2147483647 -> VK_DEVICE_DIAGNOSTICS_CONFIG_FLAG_BITS_MAX_ENUM_NV;
            default -> new VkDeviceDiagnosticsConfigFlagBitsNV(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
