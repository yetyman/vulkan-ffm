package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkTileShadingRenderPassFlagBitsQCOM
 * Generated from jextract bindings
 */
public record VkTileShadingRenderPassFlagBitsQCOM(int value) {

    public static final VkTileShadingRenderPassFlagBitsQCOM VK_TILE_SHADING_RENDER_PASS_ENABLE_BIT_QCOM = new VkTileShadingRenderPassFlagBitsQCOM(1);
    public static final VkTileShadingRenderPassFlagBitsQCOM VK_TILE_SHADING_RENDER_PASS_FLAG_BITS_MAX_ENUM_QCOM = new VkTileShadingRenderPassFlagBitsQCOM(2147483647);
    public static final VkTileShadingRenderPassFlagBitsQCOM VK_TILE_SHADING_RENDER_PASS_PER_TILE_EXECUTION_BIT_QCOM = new VkTileShadingRenderPassFlagBitsQCOM(2);

    public static VkTileShadingRenderPassFlagBitsQCOM fromValue(int value) {
        return switch (value) {
            case 1 -> VK_TILE_SHADING_RENDER_PASS_ENABLE_BIT_QCOM;
            case 2147483647 -> VK_TILE_SHADING_RENDER_PASS_FLAG_BITS_MAX_ENUM_QCOM;
            case 2 -> VK_TILE_SHADING_RENDER_PASS_PER_TILE_EXECUTION_BIT_QCOM;
            default -> new VkTileShadingRenderPassFlagBitsQCOM(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
