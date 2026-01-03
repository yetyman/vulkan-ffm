package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkRenderingAttachmentFlagBitsKHR
 * Generated from jextract bindings
 */
public record VkRenderingAttachmentFlagBitsKHR(int value) {

    public static final VkRenderingAttachmentFlagBitsKHR VK_RENDERING_ATTACHMENT_FLAG_BITS_MAX_ENUM_KHR = new VkRenderingAttachmentFlagBitsKHR(2147483647);
    public static final VkRenderingAttachmentFlagBitsKHR VK_RENDERING_ATTACHMENT_INPUT_ATTACHMENT_FEEDBACK_BIT_KHR = new VkRenderingAttachmentFlagBitsKHR(1);
    public static final VkRenderingAttachmentFlagBitsKHR VK_RENDERING_ATTACHMENT_RESOLVE_ENABLE_TRANSFER_FUNCTION_BIT_KHR = new VkRenderingAttachmentFlagBitsKHR(4);
    public static final VkRenderingAttachmentFlagBitsKHR VK_RENDERING_ATTACHMENT_RESOLVE_SKIP_TRANSFER_FUNCTION_BIT_KHR = new VkRenderingAttachmentFlagBitsKHR(2);

    public static VkRenderingAttachmentFlagBitsKHR fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_RENDERING_ATTACHMENT_FLAG_BITS_MAX_ENUM_KHR;
            case 1 -> VK_RENDERING_ATTACHMENT_INPUT_ATTACHMENT_FEEDBACK_BIT_KHR;
            case 4 -> VK_RENDERING_ATTACHMENT_RESOLVE_ENABLE_TRANSFER_FUNCTION_BIT_KHR;
            case 2 -> VK_RENDERING_ATTACHMENT_RESOLVE_SKIP_TRANSFER_FUNCTION_BIT_KHR;
            default -> new VkRenderingAttachmentFlagBitsKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
