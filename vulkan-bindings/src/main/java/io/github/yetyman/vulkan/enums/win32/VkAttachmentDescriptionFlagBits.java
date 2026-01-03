package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkAttachmentDescriptionFlagBits
 * Generated from jextract bindings
 */
public record VkAttachmentDescriptionFlagBits(int value) {

    public static final VkAttachmentDescriptionFlagBits VK_ATTACHMENT_DESCRIPTION_FLAG_BITS_MAX_ENUM = new VkAttachmentDescriptionFlagBits(2147483647);
    public static final VkAttachmentDescriptionFlagBits VK_ATTACHMENT_DESCRIPTION_MAY_ALIAS_BIT = new VkAttachmentDescriptionFlagBits(1);
    public static final VkAttachmentDescriptionFlagBits VK_ATTACHMENT_DESCRIPTION_RESOLVE_ENABLE_TRANSFER_FUNCTION_BIT_KHR = new VkAttachmentDescriptionFlagBits(4);
    public static final VkAttachmentDescriptionFlagBits VK_ATTACHMENT_DESCRIPTION_RESOLVE_SKIP_TRANSFER_FUNCTION_BIT_KHR = new VkAttachmentDescriptionFlagBits(2);

    public static VkAttachmentDescriptionFlagBits fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_ATTACHMENT_DESCRIPTION_FLAG_BITS_MAX_ENUM;
            case 1 -> VK_ATTACHMENT_DESCRIPTION_MAY_ALIAS_BIT;
            case 4 -> VK_ATTACHMENT_DESCRIPTION_RESOLVE_ENABLE_TRANSFER_FUNCTION_BIT_KHR;
            case 2 -> VK_ATTACHMENT_DESCRIPTION_RESOLVE_SKIP_TRANSFER_FUNCTION_BIT_KHR;
            default -> new VkAttachmentDescriptionFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
