package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkDeviceAddressBindingFlagBitsEXT
 * Generated from jextract bindings
 */
public record VkDeviceAddressBindingFlagBitsEXT(int value) {

    public static final VkDeviceAddressBindingFlagBitsEXT VK_DEVICE_ADDRESS_BINDING_FLAG_BITS_MAX_ENUM_EXT = new VkDeviceAddressBindingFlagBitsEXT(2147483647);
    public static final VkDeviceAddressBindingFlagBitsEXT VK_DEVICE_ADDRESS_BINDING_INTERNAL_OBJECT_BIT_EXT = new VkDeviceAddressBindingFlagBitsEXT(1);

    public static VkDeviceAddressBindingFlagBitsEXT fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_DEVICE_ADDRESS_BINDING_FLAG_BITS_MAX_ENUM_EXT;
            case 1 -> VK_DEVICE_ADDRESS_BINDING_INTERNAL_OBJECT_BIT_EXT;
            default -> new VkDeviceAddressBindingFlagBitsEXT(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
