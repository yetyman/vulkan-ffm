package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkAttachmentStoreOp
 * Generated from jextract bindings
 */
public record VkAttachmentStoreOp(int value) {

    public static final VkAttachmentStoreOp VK_ATTACHMENT_STORE_OP_DONT_CARE = new VkAttachmentStoreOp(1);
    public static final VkAttachmentStoreOp VK_ATTACHMENT_STORE_OP_MAX_ENUM = new VkAttachmentStoreOp(2147483647);
    public static final VkAttachmentStoreOp VK_ATTACHMENT_STORE_OP_NONE = new VkAttachmentStoreOp(1000301000);
    public static final VkAttachmentStoreOp VK_ATTACHMENT_STORE_OP_NONE_EXT = new VkAttachmentStoreOp(1000301000);
    public static final VkAttachmentStoreOp VK_ATTACHMENT_STORE_OP_NONE_KHR = new VkAttachmentStoreOp(1000301000);
    public static final VkAttachmentStoreOp VK_ATTACHMENT_STORE_OP_NONE_QCOM = new VkAttachmentStoreOp(1000301000);
    public static final VkAttachmentStoreOp VK_ATTACHMENT_STORE_OP_STORE = new VkAttachmentStoreOp(0);

    public static VkAttachmentStoreOp fromValue(int value) {
        return switch (value) {
            case 1 -> VK_ATTACHMENT_STORE_OP_DONT_CARE;
            case 2147483647 -> VK_ATTACHMENT_STORE_OP_MAX_ENUM;
            case 1000301000 -> VK_ATTACHMENT_STORE_OP_NONE;
            case 0 -> VK_ATTACHMENT_STORE_OP_STORE;
            default -> new VkAttachmentStoreOp(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
