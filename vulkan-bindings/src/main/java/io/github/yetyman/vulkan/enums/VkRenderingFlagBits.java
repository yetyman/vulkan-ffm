package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkRenderingFlagBits
 * Generated from jextract bindings
 */
public record VkRenderingFlagBits(int value) {

    public static final VkRenderingFlagBits VK_RENDERING_CONTENTS_INLINE_BIT_EXT = new VkRenderingFlagBits(16);
    public static final VkRenderingFlagBits VK_RENDERING_CONTENTS_INLINE_BIT_KHR = VK_RENDERING_CONTENTS_INLINE_BIT_EXT;
    public static final VkRenderingFlagBits VK_RENDERING_CONTENTS_SECONDARY_COMMAND_BUFFERS_BIT = new VkRenderingFlagBits(1);
    public static final VkRenderingFlagBits VK_RENDERING_CONTENTS_SECONDARY_COMMAND_BUFFERS_BIT_KHR = VK_RENDERING_CONTENTS_SECONDARY_COMMAND_BUFFERS_BIT;
    public static final VkRenderingFlagBits VK_RENDERING_CUSTOM_RESOLVE_BIT_EXT = new VkRenderingFlagBits(128);
    public static final VkRenderingFlagBits VK_RENDERING_ENABLE_LEGACY_DITHERING_BIT_EXT = new VkRenderingFlagBits(8);
    public static final VkRenderingFlagBits VK_RENDERING_FLAG_BITS_MAX_ENUM = new VkRenderingFlagBits(2147483647);
    public static final VkRenderingFlagBits VK_RENDERING_FRAGMENT_REGION_BIT_EXT = new VkRenderingFlagBits(64);
    public static final VkRenderingFlagBits VK_RENDERING_LOCAL_READ_CONCURRENT_ACCESS_CONTROL_BIT_KHR = new VkRenderingFlagBits(256);
    public static final VkRenderingFlagBits VK_RENDERING_PER_LAYER_FRAGMENT_DENSITY_BIT_VALVE = new VkRenderingFlagBits(32);
    public static final VkRenderingFlagBits VK_RENDERING_RESUMING_BIT = new VkRenderingFlagBits(4);
    public static final VkRenderingFlagBits VK_RENDERING_RESUMING_BIT_KHR = VK_RENDERING_RESUMING_BIT;
    public static final VkRenderingFlagBits VK_RENDERING_SUSPENDING_BIT = new VkRenderingFlagBits(2);
    public static final VkRenderingFlagBits VK_RENDERING_SUSPENDING_BIT_KHR = VK_RENDERING_SUSPENDING_BIT;

    public static VkRenderingFlagBits fromValue(int value) {
        return switch (value) {
            case 16 -> VK_RENDERING_CONTENTS_INLINE_BIT_EXT;
            case 1 -> VK_RENDERING_CONTENTS_SECONDARY_COMMAND_BUFFERS_BIT;
            case 128 -> VK_RENDERING_CUSTOM_RESOLVE_BIT_EXT;
            case 8 -> VK_RENDERING_ENABLE_LEGACY_DITHERING_BIT_EXT;
            case 2147483647 -> VK_RENDERING_FLAG_BITS_MAX_ENUM;
            case 64 -> VK_RENDERING_FRAGMENT_REGION_BIT_EXT;
            case 256 -> VK_RENDERING_LOCAL_READ_CONCURRENT_ACCESS_CONTROL_BIT_KHR;
            case 32 -> VK_RENDERING_PER_LAYER_FRAGMENT_DENSITY_BIT_VALVE;
            case 4 -> VK_RENDERING_RESUMING_BIT;
            case 2 -> VK_RENDERING_SUSPENDING_BIT;
            default -> new VkRenderingFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
