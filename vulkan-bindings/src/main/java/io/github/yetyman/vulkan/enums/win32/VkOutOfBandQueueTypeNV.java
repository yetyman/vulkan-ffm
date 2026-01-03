package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkOutOfBandQueueTypeNV
 * Generated from jextract bindings
 */
public record VkOutOfBandQueueTypeNV(int value) {

    public static final VkOutOfBandQueueTypeNV VK_OUT_OF_BAND_QUEUE_TYPE_MAX_ENUM_NV = new VkOutOfBandQueueTypeNV(2147483647);
    public static final VkOutOfBandQueueTypeNV VK_OUT_OF_BAND_QUEUE_TYPE_PRESENT_NV = new VkOutOfBandQueueTypeNV(1);
    public static final VkOutOfBandQueueTypeNV VK_OUT_OF_BAND_QUEUE_TYPE_RENDER_NV = new VkOutOfBandQueueTypeNV(0);

    public static VkOutOfBandQueueTypeNV fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_OUT_OF_BAND_QUEUE_TYPE_MAX_ENUM_NV;
            case 1 -> VK_OUT_OF_BAND_QUEUE_TYPE_PRESENT_NV;
            case 0 -> VK_OUT_OF_BAND_QUEUE_TYPE_RENDER_NV;
            default -> new VkOutOfBandQueueTypeNV(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
