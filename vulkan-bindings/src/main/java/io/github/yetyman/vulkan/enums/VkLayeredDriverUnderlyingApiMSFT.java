package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkLayeredDriverUnderlyingApiMSFT
 * Generated from jextract bindings
 */
public record VkLayeredDriverUnderlyingApiMSFT(int value) {

    public static final VkLayeredDriverUnderlyingApiMSFT VK_LAYERED_DRIVER_UNDERLYING_API_D3D12_MSFT = new VkLayeredDriverUnderlyingApiMSFT(1);
    public static final VkLayeredDriverUnderlyingApiMSFT VK_LAYERED_DRIVER_UNDERLYING_API_MAX_ENUM_MSFT = new VkLayeredDriverUnderlyingApiMSFT(2147483647);
    public static final VkLayeredDriverUnderlyingApiMSFT VK_LAYERED_DRIVER_UNDERLYING_API_NONE_MSFT = new VkLayeredDriverUnderlyingApiMSFT(0);

    public static VkLayeredDriverUnderlyingApiMSFT fromValue(int value) {
        return switch (value) {
            case 1 -> VK_LAYERED_DRIVER_UNDERLYING_API_D3D12_MSFT;
            case 2147483647 -> VK_LAYERED_DRIVER_UNDERLYING_API_MAX_ENUM_MSFT;
            case 0 -> VK_LAYERED_DRIVER_UNDERLYING_API_NONE_MSFT;
            default -> new VkLayeredDriverUnderlyingApiMSFT(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
