package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkFenceImportFlagBits
 * Generated from jextract bindings
 */
public record VkFenceImportFlagBits(int value) {

    public static final VkFenceImportFlagBits VK_FENCE_IMPORT_FLAG_BITS_MAX_ENUM = new VkFenceImportFlagBits(2147483647);
    public static final VkFenceImportFlagBits VK_FENCE_IMPORT_TEMPORARY_BIT = new VkFenceImportFlagBits(1);
    public static final VkFenceImportFlagBits VK_FENCE_IMPORT_TEMPORARY_BIT_KHR = new VkFenceImportFlagBits(1);

    public static VkFenceImportFlagBits fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_FENCE_IMPORT_FLAG_BITS_MAX_ENUM;
            case 1 -> VK_FENCE_IMPORT_TEMPORARY_BIT;
            default -> new VkFenceImportFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
