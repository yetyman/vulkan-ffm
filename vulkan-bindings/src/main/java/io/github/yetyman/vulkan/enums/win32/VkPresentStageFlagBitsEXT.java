package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkPresentStageFlagBitsEXT
 * Generated from jextract bindings
 */
public record VkPresentStageFlagBitsEXT(int value) {

    public static final VkPresentStageFlagBitsEXT VK_PRESENT_STAGE_FLAG_BITS_MAX_ENUM_EXT = new VkPresentStageFlagBitsEXT(2147483647);
    public static final VkPresentStageFlagBitsEXT VK_PRESENT_STAGE_IMAGE_FIRST_PIXEL_OUT_BIT_EXT = new VkPresentStageFlagBitsEXT(4);
    public static final VkPresentStageFlagBitsEXT VK_PRESENT_STAGE_IMAGE_FIRST_PIXEL_VISIBLE_BIT_EXT = new VkPresentStageFlagBitsEXT(8);
    public static final VkPresentStageFlagBitsEXT VK_PRESENT_STAGE_QUEUE_OPERATIONS_END_BIT_EXT = new VkPresentStageFlagBitsEXT(1);
    public static final VkPresentStageFlagBitsEXT VK_PRESENT_STAGE_REQUEST_DEQUEUED_BIT_EXT = new VkPresentStageFlagBitsEXT(2);

    public static VkPresentStageFlagBitsEXT fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_PRESENT_STAGE_FLAG_BITS_MAX_ENUM_EXT;
            case 4 -> VK_PRESENT_STAGE_IMAGE_FIRST_PIXEL_OUT_BIT_EXT;
            case 8 -> VK_PRESENT_STAGE_IMAGE_FIRST_PIXEL_VISIBLE_BIT_EXT;
            case 1 -> VK_PRESENT_STAGE_QUEUE_OPERATIONS_END_BIT_EXT;
            case 2 -> VK_PRESENT_STAGE_REQUEST_DEQUEUED_BIT_EXT;
            default -> new VkPresentStageFlagBitsEXT(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
