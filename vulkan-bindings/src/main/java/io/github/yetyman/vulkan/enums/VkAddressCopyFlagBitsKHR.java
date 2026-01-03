package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkAddressCopyFlagBitsKHR
 * Generated from jextract bindings
 */
public record VkAddressCopyFlagBitsKHR(int value) {

    public static final VkAddressCopyFlagBitsKHR VK_ADDRESS_COPY_DEVICE_LOCAL_BIT_KHR = new VkAddressCopyFlagBitsKHR(1);
    public static final VkAddressCopyFlagBitsKHR VK_ADDRESS_COPY_FLAG_BITS_MAX_ENUM_KHR = new VkAddressCopyFlagBitsKHR(2147483647);
    public static final VkAddressCopyFlagBitsKHR VK_ADDRESS_COPY_PROTECTED_BIT_KHR = new VkAddressCopyFlagBitsKHR(4);
    public static final VkAddressCopyFlagBitsKHR VK_ADDRESS_COPY_SPARSE_BIT_KHR = new VkAddressCopyFlagBitsKHR(2);

    public static VkAddressCopyFlagBitsKHR fromValue(int value) {
        return switch (value) {
            case 1 -> VK_ADDRESS_COPY_DEVICE_LOCAL_BIT_KHR;
            case 2147483647 -> VK_ADDRESS_COPY_FLAG_BITS_MAX_ENUM_KHR;
            case 4 -> VK_ADDRESS_COPY_PROTECTED_BIT_KHR;
            case 2 -> VK_ADDRESS_COPY_SPARSE_BIT_KHR;
            default -> new VkAddressCopyFlagBitsKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
