package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkDirectDriverLoadingModeLUNARG
 * Generated from jextract bindings
 */
public record VkDirectDriverLoadingModeLUNARG(int value) {

    public static final VkDirectDriverLoadingModeLUNARG VK_DIRECT_DRIVER_LOADING_MODE_EXCLUSIVE_LUNARG = new VkDirectDriverLoadingModeLUNARG(0);
    public static final VkDirectDriverLoadingModeLUNARG VK_DIRECT_DRIVER_LOADING_MODE_INCLUSIVE_LUNARG = new VkDirectDriverLoadingModeLUNARG(1);
    public static final VkDirectDriverLoadingModeLUNARG VK_DIRECT_DRIVER_LOADING_MODE_MAX_ENUM_LUNARG = new VkDirectDriverLoadingModeLUNARG(2147483647);

    public static VkDirectDriverLoadingModeLUNARG fromValue(int value) {
        return switch (value) {
            case 0 -> VK_DIRECT_DRIVER_LOADING_MODE_EXCLUSIVE_LUNARG;
            case 1 -> VK_DIRECT_DRIVER_LOADING_MODE_INCLUSIVE_LUNARG;
            case 2147483647 -> VK_DIRECT_DRIVER_LOADING_MODE_MAX_ENUM_LUNARG;
            default -> new VkDirectDriverLoadingModeLUNARG(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
