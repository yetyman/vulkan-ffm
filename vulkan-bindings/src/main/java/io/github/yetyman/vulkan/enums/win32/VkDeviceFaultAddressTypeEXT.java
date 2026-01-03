package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkDeviceFaultAddressTypeEXT
 * Generated from jextract bindings
 */
public record VkDeviceFaultAddressTypeEXT(int value) {

    public static final VkDeviceFaultAddressTypeEXT VK_DEVICE_FAULT_ADDRESS_TYPE_EXECUTE_INVALID_EXT = new VkDeviceFaultAddressTypeEXT(3);
    public static final VkDeviceFaultAddressTypeEXT VK_DEVICE_FAULT_ADDRESS_TYPE_INSTRUCTION_POINTER_FAULT_EXT = new VkDeviceFaultAddressTypeEXT(6);
    public static final VkDeviceFaultAddressTypeEXT VK_DEVICE_FAULT_ADDRESS_TYPE_INSTRUCTION_POINTER_INVALID_EXT = new VkDeviceFaultAddressTypeEXT(5);
    public static final VkDeviceFaultAddressTypeEXT VK_DEVICE_FAULT_ADDRESS_TYPE_INSTRUCTION_POINTER_UNKNOWN_EXT = new VkDeviceFaultAddressTypeEXT(4);
    public static final VkDeviceFaultAddressTypeEXT VK_DEVICE_FAULT_ADDRESS_TYPE_MAX_ENUM_EXT = new VkDeviceFaultAddressTypeEXT(2147483647);
    public static final VkDeviceFaultAddressTypeEXT VK_DEVICE_FAULT_ADDRESS_TYPE_NONE_EXT = new VkDeviceFaultAddressTypeEXT(0);
    public static final VkDeviceFaultAddressTypeEXT VK_DEVICE_FAULT_ADDRESS_TYPE_READ_INVALID_EXT = new VkDeviceFaultAddressTypeEXT(1);
    public static final VkDeviceFaultAddressTypeEXT VK_DEVICE_FAULT_ADDRESS_TYPE_WRITE_INVALID_EXT = new VkDeviceFaultAddressTypeEXT(2);

    public static VkDeviceFaultAddressTypeEXT fromValue(int value) {
        return switch (value) {
            case 3 -> VK_DEVICE_FAULT_ADDRESS_TYPE_EXECUTE_INVALID_EXT;
            case 6 -> VK_DEVICE_FAULT_ADDRESS_TYPE_INSTRUCTION_POINTER_FAULT_EXT;
            case 5 -> VK_DEVICE_FAULT_ADDRESS_TYPE_INSTRUCTION_POINTER_INVALID_EXT;
            case 4 -> VK_DEVICE_FAULT_ADDRESS_TYPE_INSTRUCTION_POINTER_UNKNOWN_EXT;
            case 2147483647 -> VK_DEVICE_FAULT_ADDRESS_TYPE_MAX_ENUM_EXT;
            case 0 -> VK_DEVICE_FAULT_ADDRESS_TYPE_NONE_EXT;
            case 1 -> VK_DEVICE_FAULT_ADDRESS_TYPE_READ_INVALID_EXT;
            case 2 -> VK_DEVICE_FAULT_ADDRESS_TYPE_WRITE_INVALID_EXT;
            default -> new VkDeviceFaultAddressTypeEXT(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
