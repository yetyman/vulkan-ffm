package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkFrontFace
 * Generated from jextract bindings
 */
public record VkFrontFace(int value) {

    public static final VkFrontFace VK_FRONT_FACE_CLOCKWISE = new VkFrontFace(1);
    public static final VkFrontFace VK_FRONT_FACE_COUNTER_CLOCKWISE = new VkFrontFace(0);
    public static final VkFrontFace VK_FRONT_FACE_MAX_ENUM = new VkFrontFace(2147483647);

    public static VkFrontFace fromValue(int value) {
        return switch (value) {
            case 1 -> VK_FRONT_FACE_CLOCKWISE;
            case 0 -> VK_FRONT_FACE_COUNTER_CLOCKWISE;
            case 2147483647 -> VK_FRONT_FACE_MAX_ENUM;
            default -> new VkFrontFace(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
