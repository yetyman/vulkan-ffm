package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkExternalMemoryHandleTypeFlagBitsNV
 * Generated from jextract bindings
 */
public record VkExternalMemoryHandleTypeFlagBitsNV(int value) {

    public static final VkExternalMemoryHandleTypeFlagBitsNV VK_EXTERNAL_MEMORY_HANDLE_TYPE_D3D11_IMAGE_BIT_NV = new VkExternalMemoryHandleTypeFlagBitsNV(4);
    public static final VkExternalMemoryHandleTypeFlagBitsNV VK_EXTERNAL_MEMORY_HANDLE_TYPE_D3D11_IMAGE_KMT_BIT_NV = new VkExternalMemoryHandleTypeFlagBitsNV(8);
    public static final VkExternalMemoryHandleTypeFlagBitsNV VK_EXTERNAL_MEMORY_HANDLE_TYPE_FLAG_BITS_MAX_ENUM_NV = new VkExternalMemoryHandleTypeFlagBitsNV(2147483647);
    public static final VkExternalMemoryHandleTypeFlagBitsNV VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT_NV = new VkExternalMemoryHandleTypeFlagBitsNV(1);
    public static final VkExternalMemoryHandleTypeFlagBitsNV VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_KMT_BIT_NV = new VkExternalMemoryHandleTypeFlagBitsNV(2);

    public static VkExternalMemoryHandleTypeFlagBitsNV fromValue(int value) {
        return switch (value) {
            case 4 -> VK_EXTERNAL_MEMORY_HANDLE_TYPE_D3D11_IMAGE_BIT_NV;
            case 8 -> VK_EXTERNAL_MEMORY_HANDLE_TYPE_D3D11_IMAGE_KMT_BIT_NV;
            case 2147483647 -> VK_EXTERNAL_MEMORY_HANDLE_TYPE_FLAG_BITS_MAX_ENUM_NV;
            case 1 -> VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT_NV;
            case 2 -> VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_KMT_BIT_NV;
            default -> new VkExternalMemoryHandleTypeFlagBitsNV(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
