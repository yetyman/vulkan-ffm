package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkIndexType
 * Generated from jextract bindings
 */
public record VkIndexType(int value) {

    public static final VkIndexType VK_INDEX_TYPE_MAX_ENUM = new VkIndexType(2147483647);
    public static final VkIndexType VK_INDEX_TYPE_NONE_KHR = new VkIndexType(1000165000);
    public static final VkIndexType VK_INDEX_TYPE_NONE_NV = VK_INDEX_TYPE_NONE_KHR;
    public static final VkIndexType VK_INDEX_TYPE_UINT16 = new VkIndexType(0);
    public static final VkIndexType VK_INDEX_TYPE_UINT32 = new VkIndexType(1);
    public static final VkIndexType VK_INDEX_TYPE_UINT8 = new VkIndexType(1000265000);
    public static final VkIndexType VK_INDEX_TYPE_UINT8_EXT = VK_INDEX_TYPE_UINT8;
    public static final VkIndexType VK_INDEX_TYPE_UINT8_KHR = VK_INDEX_TYPE_UINT8;

    public static VkIndexType fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_INDEX_TYPE_MAX_ENUM;
            case 1000165000 -> VK_INDEX_TYPE_NONE_NV;
            case 0 -> VK_INDEX_TYPE_UINT16;
            case 1 -> VK_INDEX_TYPE_UINT32;
            case 1000265000 -> VK_INDEX_TYPE_UINT8;
            default -> new VkIndexType(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
