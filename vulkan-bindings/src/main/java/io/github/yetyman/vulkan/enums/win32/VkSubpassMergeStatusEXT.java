package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkSubpassMergeStatusEXT
 * Generated from jextract bindings
 */
public record VkSubpassMergeStatusEXT(int value) {

    public static final VkSubpassMergeStatusEXT VK_SUBPASS_MERGE_STATUS_DISALLOWED_EXT = new VkSubpassMergeStatusEXT(1);
    public static final VkSubpassMergeStatusEXT VK_SUBPASS_MERGE_STATUS_MAX_ENUM_EXT = new VkSubpassMergeStatusEXT(2147483647);
    public static final VkSubpassMergeStatusEXT VK_SUBPASS_MERGE_STATUS_MERGED_EXT = new VkSubpassMergeStatusEXT(0);
    public static final VkSubpassMergeStatusEXT VK_SUBPASS_MERGE_STATUS_NOT_MERGED_ALIASING_EXT = new VkSubpassMergeStatusEXT(5);
    public static final VkSubpassMergeStatusEXT VK_SUBPASS_MERGE_STATUS_NOT_MERGED_DEPENDENCIES_EXT = new VkSubpassMergeStatusEXT(6);
    public static final VkSubpassMergeStatusEXT VK_SUBPASS_MERGE_STATUS_NOT_MERGED_DEPTH_STENCIL_COUNT_EXT = new VkSubpassMergeStatusEXT(10);
    public static final VkSubpassMergeStatusEXT VK_SUBPASS_MERGE_STATUS_NOT_MERGED_INCOMPATIBLE_INPUT_ATTACHMENT_EXT = new VkSubpassMergeStatusEXT(7);
    public static final VkSubpassMergeStatusEXT VK_SUBPASS_MERGE_STATUS_NOT_MERGED_INSUFFICIENT_STORAGE_EXT = new VkSubpassMergeStatusEXT(9);
    public static final VkSubpassMergeStatusEXT VK_SUBPASS_MERGE_STATUS_NOT_MERGED_RESOLVE_ATTACHMENT_REUSE_EXT = new VkSubpassMergeStatusEXT(11);
    public static final VkSubpassMergeStatusEXT VK_SUBPASS_MERGE_STATUS_NOT_MERGED_SAMPLES_MISMATCH_EXT = new VkSubpassMergeStatusEXT(3);
    public static final VkSubpassMergeStatusEXT VK_SUBPASS_MERGE_STATUS_NOT_MERGED_SIDE_EFFECTS_EXT = new VkSubpassMergeStatusEXT(2);
    public static final VkSubpassMergeStatusEXT VK_SUBPASS_MERGE_STATUS_NOT_MERGED_SINGLE_SUBPASS_EXT = new VkSubpassMergeStatusEXT(12);
    public static final VkSubpassMergeStatusEXT VK_SUBPASS_MERGE_STATUS_NOT_MERGED_TOO_MANY_ATTACHMENTS_EXT = new VkSubpassMergeStatusEXT(8);
    public static final VkSubpassMergeStatusEXT VK_SUBPASS_MERGE_STATUS_NOT_MERGED_UNSPECIFIED_EXT = new VkSubpassMergeStatusEXT(13);
    public static final VkSubpassMergeStatusEXT VK_SUBPASS_MERGE_STATUS_NOT_MERGED_VIEWS_MISMATCH_EXT = new VkSubpassMergeStatusEXT(4);

    public static VkSubpassMergeStatusEXT fromValue(int value) {
        return switch (value) {
            case 1 -> VK_SUBPASS_MERGE_STATUS_DISALLOWED_EXT;
            case 2147483647 -> VK_SUBPASS_MERGE_STATUS_MAX_ENUM_EXT;
            case 0 -> VK_SUBPASS_MERGE_STATUS_MERGED_EXT;
            case 5 -> VK_SUBPASS_MERGE_STATUS_NOT_MERGED_ALIASING_EXT;
            case 6 -> VK_SUBPASS_MERGE_STATUS_NOT_MERGED_DEPENDENCIES_EXT;
            case 10 -> VK_SUBPASS_MERGE_STATUS_NOT_MERGED_DEPTH_STENCIL_COUNT_EXT;
            case 7 -> VK_SUBPASS_MERGE_STATUS_NOT_MERGED_INCOMPATIBLE_INPUT_ATTACHMENT_EXT;
            case 9 -> VK_SUBPASS_MERGE_STATUS_NOT_MERGED_INSUFFICIENT_STORAGE_EXT;
            case 11 -> VK_SUBPASS_MERGE_STATUS_NOT_MERGED_RESOLVE_ATTACHMENT_REUSE_EXT;
            case 3 -> VK_SUBPASS_MERGE_STATUS_NOT_MERGED_SAMPLES_MISMATCH_EXT;
            case 2 -> VK_SUBPASS_MERGE_STATUS_NOT_MERGED_SIDE_EFFECTS_EXT;
            case 12 -> VK_SUBPASS_MERGE_STATUS_NOT_MERGED_SINGLE_SUBPASS_EXT;
            case 8 -> VK_SUBPASS_MERGE_STATUS_NOT_MERGED_TOO_MANY_ATTACHMENTS_EXT;
            case 13 -> VK_SUBPASS_MERGE_STATUS_NOT_MERGED_UNSPECIFIED_EXT;
            case 4 -> VK_SUBPASS_MERGE_STATUS_NOT_MERGED_VIEWS_MISMATCH_EXT;
            default -> new VkSubpassMergeStatusEXT(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
