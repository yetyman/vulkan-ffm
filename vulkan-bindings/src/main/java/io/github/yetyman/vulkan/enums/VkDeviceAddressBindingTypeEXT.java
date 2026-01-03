package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkDeviceAddressBindingTypeEXT
 * Generated from jextract bindings
 */
public record VkDeviceAddressBindingTypeEXT(int value) {

    public static final VkDeviceAddressBindingTypeEXT VK_DEVICE_ADDRESS_BINDING_TYPE_BIND_EXT = new VkDeviceAddressBindingTypeEXT(0);
    public static final VkDeviceAddressBindingTypeEXT VK_DEVICE_ADDRESS_BINDING_TYPE_MAX_ENUM_EXT = new VkDeviceAddressBindingTypeEXT(2147483647);
    public static final VkDeviceAddressBindingTypeEXT VK_DEVICE_ADDRESS_BINDING_TYPE_UNBIND_EXT = new VkDeviceAddressBindingTypeEXT(1);

    public static VkDeviceAddressBindingTypeEXT fromValue(int value) {
        return switch (value) {
            case 0 -> VK_DEVICE_ADDRESS_BINDING_TYPE_BIND_EXT;
            case 2147483647 -> VK_DEVICE_ADDRESS_BINDING_TYPE_MAX_ENUM_EXT;
            case 1 -> VK_DEVICE_ADDRESS_BINDING_TYPE_UNBIND_EXT;
            default -> new VkDeviceAddressBindingTypeEXT(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
