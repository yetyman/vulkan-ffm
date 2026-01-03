package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkFramebufferCreateFlagBits
 * Generated from jextract bindings
 */
public record VkFramebufferCreateFlagBits(int value) {

    public static final VkFramebufferCreateFlagBits VK_FRAMEBUFFER_CREATE_FLAG_BITS_MAX_ENUM = new VkFramebufferCreateFlagBits(2147483647);
    public static final VkFramebufferCreateFlagBits VK_FRAMEBUFFER_CREATE_IMAGELESS_BIT = new VkFramebufferCreateFlagBits(1);
    public static final VkFramebufferCreateFlagBits VK_FRAMEBUFFER_CREATE_IMAGELESS_BIT_KHR = new VkFramebufferCreateFlagBits(1);

    public static VkFramebufferCreateFlagBits fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_FRAMEBUFFER_CREATE_FLAG_BITS_MAX_ENUM;
            case 1 -> VK_FRAMEBUFFER_CREATE_IMAGELESS_BIT;
            default -> new VkFramebufferCreateFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
