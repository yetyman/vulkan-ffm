package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkPrimitiveTopology
 * Generated from jextract bindings
 */
public record VkPrimitiveTopology(int value) {

    public static final VkPrimitiveTopology VK_PRIMITIVE_TOPOLOGY_LINE_LIST = new VkPrimitiveTopology(1);
    public static final VkPrimitiveTopology VK_PRIMITIVE_TOPOLOGY_LINE_LIST_WITH_ADJACENCY = new VkPrimitiveTopology(6);
    public static final VkPrimitiveTopology VK_PRIMITIVE_TOPOLOGY_LINE_STRIP = new VkPrimitiveTopology(2);
    public static final VkPrimitiveTopology VK_PRIMITIVE_TOPOLOGY_LINE_STRIP_WITH_ADJACENCY = new VkPrimitiveTopology(7);
    public static final VkPrimitiveTopology VK_PRIMITIVE_TOPOLOGY_MAX_ENUM = new VkPrimitiveTopology(2147483647);
    public static final VkPrimitiveTopology VK_PRIMITIVE_TOPOLOGY_PATCH_LIST = new VkPrimitiveTopology(10);
    public static final VkPrimitiveTopology VK_PRIMITIVE_TOPOLOGY_POINT_LIST = new VkPrimitiveTopology(0);
    public static final VkPrimitiveTopology VK_PRIMITIVE_TOPOLOGY_TRIANGLE_FAN = new VkPrimitiveTopology(5);
    public static final VkPrimitiveTopology VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST = new VkPrimitiveTopology(3);
    public static final VkPrimitiveTopology VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST_WITH_ADJACENCY = new VkPrimitiveTopology(8);
    public static final VkPrimitiveTopology VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP = new VkPrimitiveTopology(4);
    public static final VkPrimitiveTopology VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP_WITH_ADJACENCY = new VkPrimitiveTopology(9);

    public static VkPrimitiveTopology fromValue(int value) {
        return switch (value) {
            case 1 -> VK_PRIMITIVE_TOPOLOGY_LINE_LIST;
            case 6 -> VK_PRIMITIVE_TOPOLOGY_LINE_LIST_WITH_ADJACENCY;
            case 2 -> VK_PRIMITIVE_TOPOLOGY_LINE_STRIP;
            case 7 -> VK_PRIMITIVE_TOPOLOGY_LINE_STRIP_WITH_ADJACENCY;
            case 2147483647 -> VK_PRIMITIVE_TOPOLOGY_MAX_ENUM;
            case 10 -> VK_PRIMITIVE_TOPOLOGY_PATCH_LIST;
            case 0 -> VK_PRIMITIVE_TOPOLOGY_POINT_LIST;
            case 5 -> VK_PRIMITIVE_TOPOLOGY_TRIANGLE_FAN;
            case 3 -> VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
            case 8 -> VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST_WITH_ADJACENCY;
            case 4 -> VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP;
            case 9 -> VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP_WITH_ADJACENCY;
            default -> new VkPrimitiveTopology(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
