package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkStencilOp
 * Generated from jextract bindings
 */
public record VkStencilOp(int value) {

    public static final VkStencilOp VK_STENCIL_OP_DECREMENT_AND_CLAMP = new VkStencilOp(4);
    public static final VkStencilOp VK_STENCIL_OP_DECREMENT_AND_WRAP = new VkStencilOp(7);
    public static final VkStencilOp VK_STENCIL_OP_INCREMENT_AND_CLAMP = new VkStencilOp(3);
    public static final VkStencilOp VK_STENCIL_OP_INCREMENT_AND_WRAP = new VkStencilOp(6);
    public static final VkStencilOp VK_STENCIL_OP_INVERT = new VkStencilOp(5);
    public static final VkStencilOp VK_STENCIL_OP_KEEP = new VkStencilOp(0);
    public static final VkStencilOp VK_STENCIL_OP_MAX_ENUM = new VkStencilOp(2147483647);
    public static final VkStencilOp VK_STENCIL_OP_REPLACE = new VkStencilOp(2);
    public static final VkStencilOp VK_STENCIL_OP_ZERO = new VkStencilOp(1);

    public static VkStencilOp fromValue(int value) {
        return switch (value) {
            case 4 -> VK_STENCIL_OP_DECREMENT_AND_CLAMP;
            case 7 -> VK_STENCIL_OP_DECREMENT_AND_WRAP;
            case 3 -> VK_STENCIL_OP_INCREMENT_AND_CLAMP;
            case 6 -> VK_STENCIL_OP_INCREMENT_AND_WRAP;
            case 5 -> VK_STENCIL_OP_INVERT;
            case 0 -> VK_STENCIL_OP_KEEP;
            case 2147483647 -> VK_STENCIL_OP_MAX_ENUM;
            case 2 -> VK_STENCIL_OP_REPLACE;
            case 1 -> VK_STENCIL_OP_ZERO;
            default -> new VkStencilOp(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
