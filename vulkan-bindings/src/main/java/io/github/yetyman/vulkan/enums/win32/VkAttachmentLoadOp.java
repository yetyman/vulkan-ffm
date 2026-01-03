package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkAttachmentLoadOp
 * Generated from jextract bindings
 */
public record VkAttachmentLoadOp(int value) {

    public static final VkAttachmentLoadOp VK_ATTACHMENT_LOAD_OP_CLEAR = new VkAttachmentLoadOp(1);
    public static final VkAttachmentLoadOp VK_ATTACHMENT_LOAD_OP_DONT_CARE = new VkAttachmentLoadOp(2);
    public static final VkAttachmentLoadOp VK_ATTACHMENT_LOAD_OP_LOAD = new VkAttachmentLoadOp(0);
    public static final VkAttachmentLoadOp VK_ATTACHMENT_LOAD_OP_MAX_ENUM = new VkAttachmentLoadOp(2147483647);
    public static final VkAttachmentLoadOp VK_ATTACHMENT_LOAD_OP_NONE = new VkAttachmentLoadOp(1000400000);
    public static final VkAttachmentLoadOp VK_ATTACHMENT_LOAD_OP_NONE_EXT = new VkAttachmentLoadOp(1000400000);
    public static final VkAttachmentLoadOp VK_ATTACHMENT_LOAD_OP_NONE_KHR = new VkAttachmentLoadOp(1000400000);

    public static VkAttachmentLoadOp fromValue(int value) {
        return switch (value) {
            case 1 -> VK_ATTACHMENT_LOAD_OP_CLEAR;
            case 2 -> VK_ATTACHMENT_LOAD_OP_DONT_CARE;
            case 0 -> VK_ATTACHMENT_LOAD_OP_LOAD;
            case 2147483647 -> VK_ATTACHMENT_LOAD_OP_MAX_ENUM;
            case 1000400000 -> VK_ATTACHMENT_LOAD_OP_NONE;
            default -> new VkAttachmentLoadOp(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
