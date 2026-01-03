package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkDefaultVertexAttributeValueKHR
 * Generated from jextract bindings
 */
public record VkDefaultVertexAttributeValueKHR(int value) {

    public static final VkDefaultVertexAttributeValueKHR VK_DEFAULT_VERTEX_ATTRIBUTE_VALUE_MAX_ENUM_KHR = new VkDefaultVertexAttributeValueKHR(2147483647);
    public static final VkDefaultVertexAttributeValueKHR VK_DEFAULT_VERTEX_ATTRIBUTE_VALUE_ZERO_ZERO_ZERO_ONE_KHR = new VkDefaultVertexAttributeValueKHR(1);
    public static final VkDefaultVertexAttributeValueKHR VK_DEFAULT_VERTEX_ATTRIBUTE_VALUE_ZERO_ZERO_ZERO_ZERO_KHR = new VkDefaultVertexAttributeValueKHR(0);

    public static VkDefaultVertexAttributeValueKHR fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_DEFAULT_VERTEX_ATTRIBUTE_VALUE_MAX_ENUM_KHR;
            case 1 -> VK_DEFAULT_VERTEX_ATTRIBUTE_VALUE_ZERO_ZERO_ZERO_ONE_KHR;
            case 0 -> VK_DEFAULT_VERTEX_ATTRIBUTE_VALUE_ZERO_ZERO_ZERO_ZERO_KHR;
            default -> new VkDefaultVertexAttributeValueKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
