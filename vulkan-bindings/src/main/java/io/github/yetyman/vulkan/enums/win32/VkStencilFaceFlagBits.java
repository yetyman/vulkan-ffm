package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkStencilFaceFlagBits
 * Generated from jextract bindings
 */
public record VkStencilFaceFlagBits(int value) {

    public static final VkStencilFaceFlagBits VK_STENCIL_FACE_BACK_BIT = new VkStencilFaceFlagBits(2);
    public static final VkStencilFaceFlagBits VK_STENCIL_FACE_FLAG_BITS_MAX_ENUM = new VkStencilFaceFlagBits(2147483647);
    public static final VkStencilFaceFlagBits VK_STENCIL_FACE_FRONT_AND_BACK = new VkStencilFaceFlagBits(3);
    public static final VkStencilFaceFlagBits VK_STENCIL_FACE_FRONT_BIT = new VkStencilFaceFlagBits(1);
    public static final VkStencilFaceFlagBits VK_STENCIL_FRONT_AND_BACK = new VkStencilFaceFlagBits(3);

    public static VkStencilFaceFlagBits fromValue(int value) {
        return switch (value) {
            case 2 -> VK_STENCIL_FACE_BACK_BIT;
            case 2147483647 -> VK_STENCIL_FACE_FLAG_BITS_MAX_ENUM;
            case 3 -> VK_STENCIL_FRONT_AND_BACK;
            case 1 -> VK_STENCIL_FACE_FRONT_BIT;
            default -> new VkStencilFaceFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
