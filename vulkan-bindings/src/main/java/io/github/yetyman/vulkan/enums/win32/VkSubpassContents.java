package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkSubpassContents
 * Generated from jextract bindings
 */
public record VkSubpassContents(int value) {

    public static final VkSubpassContents VK_SUBPASS_CONTENTS_INLINE = new VkSubpassContents(0);
    public static final VkSubpassContents VK_SUBPASS_CONTENTS_INLINE_AND_SECONDARY_COMMAND_BUFFERS_EXT = new VkSubpassContents(1000451000);
    public static final VkSubpassContents VK_SUBPASS_CONTENTS_INLINE_AND_SECONDARY_COMMAND_BUFFERS_KHR = new VkSubpassContents(1000451000);
    public static final VkSubpassContents VK_SUBPASS_CONTENTS_MAX_ENUM = new VkSubpassContents(2147483647);
    public static final VkSubpassContents VK_SUBPASS_CONTENTS_SECONDARY_COMMAND_BUFFERS = new VkSubpassContents(1);

    public static VkSubpassContents fromValue(int value) {
        return switch (value) {
            case 0 -> VK_SUBPASS_CONTENTS_INLINE;
            case 1000451000 -> VK_SUBPASS_CONTENTS_INLINE_AND_SECONDARY_COMMAND_BUFFERS_EXT;
            case 2147483647 -> VK_SUBPASS_CONTENTS_MAX_ENUM;
            case 1 -> VK_SUBPASS_CONTENTS_SECONDARY_COMMAND_BUFFERS;
            default -> new VkSubpassContents(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
