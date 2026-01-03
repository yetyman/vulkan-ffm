package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkProvokingVertexModeEXT
 * Generated from jextract bindings
 */
public record VkProvokingVertexModeEXT(int value) {

    public static final VkProvokingVertexModeEXT VK_PROVOKING_VERTEX_MODE_FIRST_VERTEX_EXT = new VkProvokingVertexModeEXT(0);
    public static final VkProvokingVertexModeEXT VK_PROVOKING_VERTEX_MODE_LAST_VERTEX_EXT = new VkProvokingVertexModeEXT(1);
    public static final VkProvokingVertexModeEXT VK_PROVOKING_VERTEX_MODE_MAX_ENUM_EXT = new VkProvokingVertexModeEXT(2147483647);

    public static VkProvokingVertexModeEXT fromValue(int value) {
        return switch (value) {
            case 0 -> VK_PROVOKING_VERTEX_MODE_FIRST_VERTEX_EXT;
            case 1 -> VK_PROVOKING_VERTEX_MODE_LAST_VERTEX_EXT;
            case 2147483647 -> VK_PROVOKING_VERTEX_MODE_MAX_ENUM_EXT;
            default -> new VkProvokingVertexModeEXT(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
