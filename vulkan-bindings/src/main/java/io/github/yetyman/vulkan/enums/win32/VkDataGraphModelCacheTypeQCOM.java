package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkDataGraphModelCacheTypeQCOM
 * Generated from jextract bindings
 */
public record VkDataGraphModelCacheTypeQCOM(int value) {

    public static final VkDataGraphModelCacheTypeQCOM VK_DATA_GRAPH_MODEL_CACHE_TYPE_GENERIC_BINARY_QCOM = new VkDataGraphModelCacheTypeQCOM(0);
    public static final VkDataGraphModelCacheTypeQCOM VK_DATA_GRAPH_MODEL_CACHE_TYPE_MAX_ENUM_QCOM = new VkDataGraphModelCacheTypeQCOM(2147483647);

    public static VkDataGraphModelCacheTypeQCOM fromValue(int value) {
        return switch (value) {
            case 0 -> VK_DATA_GRAPH_MODEL_CACHE_TYPE_GENERIC_BINARY_QCOM;
            case 2147483647 -> VK_DATA_GRAPH_MODEL_CACHE_TYPE_MAX_ENUM_QCOM;
            default -> new VkDataGraphModelCacheTypeQCOM(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
