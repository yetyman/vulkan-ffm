package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkShadingRatePaletteEntryNV
 * Generated from jextract bindings
 */
public record VkShadingRatePaletteEntryNV(int value) {

    public static final VkShadingRatePaletteEntryNV VK_SHADING_RATE_PALETTE_ENTRY_16_INVOCATIONS_PER_PIXEL_NV = new VkShadingRatePaletteEntryNV(1);
    public static final VkShadingRatePaletteEntryNV VK_SHADING_RATE_PALETTE_ENTRY_1_INVOCATION_PER_1X2_PIXELS_NV = new VkShadingRatePaletteEntryNV(7);
    public static final VkShadingRatePaletteEntryNV VK_SHADING_RATE_PALETTE_ENTRY_1_INVOCATION_PER_2X1_PIXELS_NV = new VkShadingRatePaletteEntryNV(6);
    public static final VkShadingRatePaletteEntryNV VK_SHADING_RATE_PALETTE_ENTRY_1_INVOCATION_PER_2X2_PIXELS_NV = new VkShadingRatePaletteEntryNV(8);
    public static final VkShadingRatePaletteEntryNV VK_SHADING_RATE_PALETTE_ENTRY_1_INVOCATION_PER_2X4_PIXELS_NV = new VkShadingRatePaletteEntryNV(10);
    public static final VkShadingRatePaletteEntryNV VK_SHADING_RATE_PALETTE_ENTRY_1_INVOCATION_PER_4X2_PIXELS_NV = new VkShadingRatePaletteEntryNV(9);
    public static final VkShadingRatePaletteEntryNV VK_SHADING_RATE_PALETTE_ENTRY_1_INVOCATION_PER_4X4_PIXELS_NV = new VkShadingRatePaletteEntryNV(11);
    public static final VkShadingRatePaletteEntryNV VK_SHADING_RATE_PALETTE_ENTRY_1_INVOCATION_PER_PIXEL_NV = new VkShadingRatePaletteEntryNV(5);
    public static final VkShadingRatePaletteEntryNV VK_SHADING_RATE_PALETTE_ENTRY_2_INVOCATIONS_PER_PIXEL_NV = new VkShadingRatePaletteEntryNV(4);
    public static final VkShadingRatePaletteEntryNV VK_SHADING_RATE_PALETTE_ENTRY_4_INVOCATIONS_PER_PIXEL_NV = new VkShadingRatePaletteEntryNV(3);
    public static final VkShadingRatePaletteEntryNV VK_SHADING_RATE_PALETTE_ENTRY_8_INVOCATIONS_PER_PIXEL_NV = new VkShadingRatePaletteEntryNV(2);
    public static final VkShadingRatePaletteEntryNV VK_SHADING_RATE_PALETTE_ENTRY_MAX_ENUM_NV = new VkShadingRatePaletteEntryNV(2147483647);
    public static final VkShadingRatePaletteEntryNV VK_SHADING_RATE_PALETTE_ENTRY_NO_INVOCATIONS_NV = new VkShadingRatePaletteEntryNV(0);

    public static VkShadingRatePaletteEntryNV fromValue(int value) {
        return switch (value) {
            case 1 -> VK_SHADING_RATE_PALETTE_ENTRY_16_INVOCATIONS_PER_PIXEL_NV;
            case 7 -> VK_SHADING_RATE_PALETTE_ENTRY_1_INVOCATION_PER_1X2_PIXELS_NV;
            case 6 -> VK_SHADING_RATE_PALETTE_ENTRY_1_INVOCATION_PER_2X1_PIXELS_NV;
            case 8 -> VK_SHADING_RATE_PALETTE_ENTRY_1_INVOCATION_PER_2X2_PIXELS_NV;
            case 10 -> VK_SHADING_RATE_PALETTE_ENTRY_1_INVOCATION_PER_2X4_PIXELS_NV;
            case 9 -> VK_SHADING_RATE_PALETTE_ENTRY_1_INVOCATION_PER_4X2_PIXELS_NV;
            case 11 -> VK_SHADING_RATE_PALETTE_ENTRY_1_INVOCATION_PER_4X4_PIXELS_NV;
            case 5 -> VK_SHADING_RATE_PALETTE_ENTRY_1_INVOCATION_PER_PIXEL_NV;
            case 4 -> VK_SHADING_RATE_PALETTE_ENTRY_2_INVOCATIONS_PER_PIXEL_NV;
            case 3 -> VK_SHADING_RATE_PALETTE_ENTRY_4_INVOCATIONS_PER_PIXEL_NV;
            case 2 -> VK_SHADING_RATE_PALETTE_ENTRY_8_INVOCATIONS_PER_PIXEL_NV;
            case 2147483647 -> VK_SHADING_RATE_PALETTE_ENTRY_MAX_ENUM_NV;
            case 0 -> VK_SHADING_RATE_PALETTE_ENTRY_NO_INVOCATIONS_NV;
            default -> new VkShadingRatePaletteEntryNV(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
