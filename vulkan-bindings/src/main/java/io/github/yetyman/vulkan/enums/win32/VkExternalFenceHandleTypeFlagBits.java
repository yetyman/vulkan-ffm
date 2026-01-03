package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkExternalFenceHandleTypeFlagBits
 * Generated from jextract bindings
 */
public record VkExternalFenceHandleTypeFlagBits(int value) {

    public static final VkExternalFenceHandleTypeFlagBits VK_EXTERNAL_FENCE_HANDLE_TYPE_FLAG_BITS_MAX_ENUM = new VkExternalFenceHandleTypeFlagBits(2147483647);
    public static final VkExternalFenceHandleTypeFlagBits VK_EXTERNAL_FENCE_HANDLE_TYPE_OPAQUE_FD_BIT = new VkExternalFenceHandleTypeFlagBits(1);
    public static final VkExternalFenceHandleTypeFlagBits VK_EXTERNAL_FENCE_HANDLE_TYPE_OPAQUE_FD_BIT_KHR = new VkExternalFenceHandleTypeFlagBits(1);
    public static final VkExternalFenceHandleTypeFlagBits VK_EXTERNAL_FENCE_HANDLE_TYPE_OPAQUE_WIN32_BIT = new VkExternalFenceHandleTypeFlagBits(2);
    public static final VkExternalFenceHandleTypeFlagBits VK_EXTERNAL_FENCE_HANDLE_TYPE_OPAQUE_WIN32_BIT_KHR = new VkExternalFenceHandleTypeFlagBits(2);
    public static final VkExternalFenceHandleTypeFlagBits VK_EXTERNAL_FENCE_HANDLE_TYPE_OPAQUE_WIN32_KMT_BIT = new VkExternalFenceHandleTypeFlagBits(4);
    public static final VkExternalFenceHandleTypeFlagBits VK_EXTERNAL_FENCE_HANDLE_TYPE_OPAQUE_WIN32_KMT_BIT_KHR = new VkExternalFenceHandleTypeFlagBits(4);
    public static final VkExternalFenceHandleTypeFlagBits VK_EXTERNAL_FENCE_HANDLE_TYPE_SYNC_FD_BIT = new VkExternalFenceHandleTypeFlagBits(8);
    public static final VkExternalFenceHandleTypeFlagBits VK_EXTERNAL_FENCE_HANDLE_TYPE_SYNC_FD_BIT_KHR = new VkExternalFenceHandleTypeFlagBits(8);

    public static VkExternalFenceHandleTypeFlagBits fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_EXTERNAL_FENCE_HANDLE_TYPE_FLAG_BITS_MAX_ENUM;
            case 1 -> VK_EXTERNAL_FENCE_HANDLE_TYPE_OPAQUE_FD_BIT;
            case 2 -> VK_EXTERNAL_FENCE_HANDLE_TYPE_OPAQUE_WIN32_BIT;
            case 4 -> VK_EXTERNAL_FENCE_HANDLE_TYPE_OPAQUE_WIN32_KMT_BIT;
            case 8 -> VK_EXTERNAL_FENCE_HANDLE_TYPE_SYNC_FD_BIT;
            default -> new VkExternalFenceHandleTypeFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
