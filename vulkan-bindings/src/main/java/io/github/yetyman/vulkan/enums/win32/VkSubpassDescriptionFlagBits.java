package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkSubpassDescriptionFlagBits
 * Generated from jextract bindings
 */
public record VkSubpassDescriptionFlagBits(int value) {

    public static final VkSubpassDescriptionFlagBits VK_SUBPASS_DESCRIPTION_CUSTOM_RESOLVE_BIT_EXT = new VkSubpassDescriptionFlagBits(8);
    public static final VkSubpassDescriptionFlagBits VK_SUBPASS_DESCRIPTION_ENABLE_LEGACY_DITHERING_BIT_EXT = new VkSubpassDescriptionFlagBits(128);
    public static final VkSubpassDescriptionFlagBits VK_SUBPASS_DESCRIPTION_FLAG_BITS_MAX_ENUM = new VkSubpassDescriptionFlagBits(2147483647);
    public static final VkSubpassDescriptionFlagBits VK_SUBPASS_DESCRIPTION_FRAGMENT_REGION_BIT_EXT = new VkSubpassDescriptionFlagBits(4);
    public static final VkSubpassDescriptionFlagBits VK_SUBPASS_DESCRIPTION_FRAGMENT_REGION_BIT_QCOM = new VkSubpassDescriptionFlagBits(4);
    public static final VkSubpassDescriptionFlagBits VK_SUBPASS_DESCRIPTION_PER_VIEW_ATTRIBUTES_BIT_NVX = new VkSubpassDescriptionFlagBits(1);
    public static final VkSubpassDescriptionFlagBits VK_SUBPASS_DESCRIPTION_PER_VIEW_POSITION_X_ONLY_BIT_NVX = new VkSubpassDescriptionFlagBits(2);
    public static final VkSubpassDescriptionFlagBits VK_SUBPASS_DESCRIPTION_RASTERIZATION_ORDER_ATTACHMENT_COLOR_ACCESS_BIT_ARM = new VkSubpassDescriptionFlagBits(16);
    public static final VkSubpassDescriptionFlagBits VK_SUBPASS_DESCRIPTION_RASTERIZATION_ORDER_ATTACHMENT_COLOR_ACCESS_BIT_EXT = new VkSubpassDescriptionFlagBits(16);
    public static final VkSubpassDescriptionFlagBits VK_SUBPASS_DESCRIPTION_RASTERIZATION_ORDER_ATTACHMENT_DEPTH_ACCESS_BIT_ARM = new VkSubpassDescriptionFlagBits(32);
    public static final VkSubpassDescriptionFlagBits VK_SUBPASS_DESCRIPTION_RASTERIZATION_ORDER_ATTACHMENT_DEPTH_ACCESS_BIT_EXT = new VkSubpassDescriptionFlagBits(32);
    public static final VkSubpassDescriptionFlagBits VK_SUBPASS_DESCRIPTION_RASTERIZATION_ORDER_ATTACHMENT_STENCIL_ACCESS_BIT_ARM = new VkSubpassDescriptionFlagBits(64);
    public static final VkSubpassDescriptionFlagBits VK_SUBPASS_DESCRIPTION_RASTERIZATION_ORDER_ATTACHMENT_STENCIL_ACCESS_BIT_EXT = new VkSubpassDescriptionFlagBits(64);
    public static final VkSubpassDescriptionFlagBits VK_SUBPASS_DESCRIPTION_SHADER_RESOLVE_BIT_QCOM = new VkSubpassDescriptionFlagBits(8);
    public static final VkSubpassDescriptionFlagBits VK_SUBPASS_DESCRIPTION_TILE_SHADING_APRON_BIT_QCOM = new VkSubpassDescriptionFlagBits(256);

    public static VkSubpassDescriptionFlagBits fromValue(int value) {
        return switch (value) {
            case 8 -> VK_SUBPASS_DESCRIPTION_SHADER_RESOLVE_BIT_QCOM;
            case 128 -> VK_SUBPASS_DESCRIPTION_ENABLE_LEGACY_DITHERING_BIT_EXT;
            case 2147483647 -> VK_SUBPASS_DESCRIPTION_FLAG_BITS_MAX_ENUM;
            case 4 -> VK_SUBPASS_DESCRIPTION_FRAGMENT_REGION_BIT_QCOM;
            case 1 -> VK_SUBPASS_DESCRIPTION_PER_VIEW_ATTRIBUTES_BIT_NVX;
            case 2 -> VK_SUBPASS_DESCRIPTION_PER_VIEW_POSITION_X_ONLY_BIT_NVX;
            case 16 -> VK_SUBPASS_DESCRIPTION_RASTERIZATION_ORDER_ATTACHMENT_COLOR_ACCESS_BIT_ARM;
            case 32 -> VK_SUBPASS_DESCRIPTION_RASTERIZATION_ORDER_ATTACHMENT_DEPTH_ACCESS_BIT_ARM;
            case 64 -> VK_SUBPASS_DESCRIPTION_RASTERIZATION_ORDER_ATTACHMENT_STENCIL_ACCESS_BIT_ARM;
            case 256 -> VK_SUBPASS_DESCRIPTION_TILE_SHADING_APRON_BIT_QCOM;
            default -> new VkSubpassDescriptionFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
