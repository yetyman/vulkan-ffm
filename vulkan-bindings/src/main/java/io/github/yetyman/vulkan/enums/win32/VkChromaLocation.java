package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkChromaLocation
 * Generated from jextract bindings
 */
public record VkChromaLocation(int value) {

    public static final VkChromaLocation VK_CHROMA_LOCATION_COSITED_EVEN = new VkChromaLocation(0);
    public static final VkChromaLocation VK_CHROMA_LOCATION_COSITED_EVEN_KHR = new VkChromaLocation(0);
    public static final VkChromaLocation VK_CHROMA_LOCATION_MAX_ENUM = new VkChromaLocation(2147483647);
    public static final VkChromaLocation VK_CHROMA_LOCATION_MIDPOINT = new VkChromaLocation(1);
    public static final VkChromaLocation VK_CHROMA_LOCATION_MIDPOINT_KHR = new VkChromaLocation(1);

    public static VkChromaLocation fromValue(int value) {
        return switch (value) {
            case 0 -> VK_CHROMA_LOCATION_COSITED_EVEN;
            case 2147483647 -> VK_CHROMA_LOCATION_MAX_ENUM;
            case 1 -> VK_CHROMA_LOCATION_MIDPOINT;
            default -> new VkChromaLocation(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
