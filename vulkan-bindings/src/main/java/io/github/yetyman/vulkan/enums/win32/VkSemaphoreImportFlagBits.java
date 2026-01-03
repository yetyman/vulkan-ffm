package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkSemaphoreImportFlagBits
 * Generated from jextract bindings
 */
public record VkSemaphoreImportFlagBits(int value) {

    public static final VkSemaphoreImportFlagBits VK_SEMAPHORE_IMPORT_FLAG_BITS_MAX_ENUM = new VkSemaphoreImportFlagBits(2147483647);
    public static final VkSemaphoreImportFlagBits VK_SEMAPHORE_IMPORT_TEMPORARY_BIT = new VkSemaphoreImportFlagBits(1);
    public static final VkSemaphoreImportFlagBits VK_SEMAPHORE_IMPORT_TEMPORARY_BIT_KHR = new VkSemaphoreImportFlagBits(1);

    public static VkSemaphoreImportFlagBits fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_SEMAPHORE_IMPORT_FLAG_BITS_MAX_ENUM;
            case 1 -> VK_SEMAPHORE_IMPORT_TEMPORARY_BIT;
            default -> new VkSemaphoreImportFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
