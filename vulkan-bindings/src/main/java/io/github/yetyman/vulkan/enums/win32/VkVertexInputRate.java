package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkVertexInputRate
 * Generated from jextract bindings
 */
public record VkVertexInputRate(int value) {

    public static final VkVertexInputRate VK_VERTEX_INPUT_RATE_INSTANCE = new VkVertexInputRate(1);
    public static final VkVertexInputRate VK_VERTEX_INPUT_RATE_MAX_ENUM = new VkVertexInputRate(2147483647);
    public static final VkVertexInputRate VK_VERTEX_INPUT_RATE_VERTEX = new VkVertexInputRate(0);

    public static VkVertexInputRate fromValue(int value) {
        return switch (value) {
            case 1 -> VK_VERTEX_INPUT_RATE_INSTANCE;
            case 2147483647 -> VK_VERTEX_INPUT_RATE_MAX_ENUM;
            case 0 -> VK_VERTEX_INPUT_RATE_VERTEX;
            default -> new VkVertexInputRate(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
