package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkDeviceFaultVendorBinaryHeaderVersionEXT
 * Generated from jextract bindings
 */
public record VkDeviceFaultVendorBinaryHeaderVersionEXT(int value) {

    public static final VkDeviceFaultVendorBinaryHeaderVersionEXT VK_DEVICE_FAULT_VENDOR_BINARY_HEADER_VERSION_MAX_ENUM_EXT = new VkDeviceFaultVendorBinaryHeaderVersionEXT(2147483647);
    public static final VkDeviceFaultVendorBinaryHeaderVersionEXT VK_DEVICE_FAULT_VENDOR_BINARY_HEADER_VERSION_ONE_EXT = new VkDeviceFaultVendorBinaryHeaderVersionEXT(1);

    public static VkDeviceFaultVendorBinaryHeaderVersionEXT fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_DEVICE_FAULT_VENDOR_BINARY_HEADER_VERSION_MAX_ENUM_EXT;
            case 1 -> VK_DEVICE_FAULT_VENDOR_BINARY_HEADER_VERSION_ONE_EXT;
            default -> new VkDeviceFaultVendorBinaryHeaderVersionEXT(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
