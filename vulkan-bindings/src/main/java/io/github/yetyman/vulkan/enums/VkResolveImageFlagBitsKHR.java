package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkResolveImageFlagBitsKHR
 * Generated from jextract bindings
 */
public record VkResolveImageFlagBitsKHR(int value) {

    public static final VkResolveImageFlagBitsKHR VK_RESOLVE_IMAGE_ENABLE_TRANSFER_FUNCTION_BIT_KHR = new VkResolveImageFlagBitsKHR(2);
    public static final VkResolveImageFlagBitsKHR VK_RESOLVE_IMAGE_FLAG_BITS_MAX_ENUM_KHR = new VkResolveImageFlagBitsKHR(2147483647);
    public static final VkResolveImageFlagBitsKHR VK_RESOLVE_IMAGE_SKIP_TRANSFER_FUNCTION_BIT_KHR = new VkResolveImageFlagBitsKHR(1);

    public static VkResolveImageFlagBitsKHR fromValue(int value) {
        return switch (value) {
            case 2 -> VK_RESOLVE_IMAGE_ENABLE_TRANSFER_FUNCTION_BIT_KHR;
            case 2147483647 -> VK_RESOLVE_IMAGE_FLAG_BITS_MAX_ENUM_KHR;
            case 1 -> VK_RESOLVE_IMAGE_SKIP_TRANSFER_FUNCTION_BIT_KHR;
            default -> new VkResolveImageFlagBitsKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
