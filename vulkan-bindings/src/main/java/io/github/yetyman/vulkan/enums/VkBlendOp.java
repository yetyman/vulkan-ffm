package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkBlendOp
 * Generated from jextract bindings
 */
public record VkBlendOp(int value) {

    public static final VkBlendOp VK_BLEND_OP_ADD = new VkBlendOp(0);
    public static final VkBlendOp VK_BLEND_OP_BLUE_EXT = new VkBlendOp(1000148045);
    public static final VkBlendOp VK_BLEND_OP_COLORBURN_EXT = new VkBlendOp(1000148018);
    public static final VkBlendOp VK_BLEND_OP_COLORDODGE_EXT = new VkBlendOp(1000148017);
    public static final VkBlendOp VK_BLEND_OP_CONTRAST_EXT = new VkBlendOp(1000148041);
    public static final VkBlendOp VK_BLEND_OP_DARKEN_EXT = new VkBlendOp(1000148015);
    public static final VkBlendOp VK_BLEND_OP_DIFFERENCE_EXT = new VkBlendOp(1000148021);
    public static final VkBlendOp VK_BLEND_OP_DST_ATOP_EXT = new VkBlendOp(1000148010);
    public static final VkBlendOp VK_BLEND_OP_DST_EXT = new VkBlendOp(1000148002);
    public static final VkBlendOp VK_BLEND_OP_DST_IN_EXT = new VkBlendOp(1000148006);
    public static final VkBlendOp VK_BLEND_OP_DST_OUT_EXT = new VkBlendOp(1000148008);
    public static final VkBlendOp VK_BLEND_OP_DST_OVER_EXT = new VkBlendOp(1000148004);
    public static final VkBlendOp VK_BLEND_OP_EXCLUSION_EXT = new VkBlendOp(1000148022);
    public static final VkBlendOp VK_BLEND_OP_GREEN_EXT = new VkBlendOp(1000148044);
    public static final VkBlendOp VK_BLEND_OP_HARDLIGHT_EXT = new VkBlendOp(1000148019);
    public static final VkBlendOp VK_BLEND_OP_HARDMIX_EXT = new VkBlendOp(1000148030);
    public static final VkBlendOp VK_BLEND_OP_HSL_COLOR_EXT = new VkBlendOp(1000148033);
    public static final VkBlendOp VK_BLEND_OP_HSL_HUE_EXT = new VkBlendOp(1000148031);
    public static final VkBlendOp VK_BLEND_OP_HSL_LUMINOSITY_EXT = new VkBlendOp(1000148034);
    public static final VkBlendOp VK_BLEND_OP_HSL_SATURATION_EXT = new VkBlendOp(1000148032);
    public static final VkBlendOp VK_BLEND_OP_INVERT_EXT = new VkBlendOp(1000148023);
    public static final VkBlendOp VK_BLEND_OP_INVERT_OVG_EXT = new VkBlendOp(1000148042);
    public static final VkBlendOp VK_BLEND_OP_INVERT_RGB_EXT = new VkBlendOp(1000148024);
    public static final VkBlendOp VK_BLEND_OP_LIGHTEN_EXT = new VkBlendOp(1000148016);
    public static final VkBlendOp VK_BLEND_OP_LINEARBURN_EXT = new VkBlendOp(1000148026);
    public static final VkBlendOp VK_BLEND_OP_LINEARDODGE_EXT = new VkBlendOp(1000148025);
    public static final VkBlendOp VK_BLEND_OP_LINEARLIGHT_EXT = new VkBlendOp(1000148028);
    public static final VkBlendOp VK_BLEND_OP_MAX = new VkBlendOp(4);
    public static final VkBlendOp VK_BLEND_OP_MAX_ENUM = new VkBlendOp(2147483647);
    public static final VkBlendOp VK_BLEND_OP_MIN = new VkBlendOp(3);
    public static final VkBlendOp VK_BLEND_OP_MINUS_CLAMPED_EXT = new VkBlendOp(1000148040);
    public static final VkBlendOp VK_BLEND_OP_MINUS_EXT = new VkBlendOp(1000148039);
    public static final VkBlendOp VK_BLEND_OP_MULTIPLY_EXT = new VkBlendOp(1000148012);
    public static final VkBlendOp VK_BLEND_OP_OVERLAY_EXT = new VkBlendOp(1000148014);
    public static final VkBlendOp VK_BLEND_OP_PINLIGHT_EXT = new VkBlendOp(1000148029);
    public static final VkBlendOp VK_BLEND_OP_PLUS_CLAMPED_ALPHA_EXT = new VkBlendOp(1000148037);
    public static final VkBlendOp VK_BLEND_OP_PLUS_CLAMPED_EXT = new VkBlendOp(1000148036);
    public static final VkBlendOp VK_BLEND_OP_PLUS_DARKER_EXT = new VkBlendOp(1000148038);
    public static final VkBlendOp VK_BLEND_OP_PLUS_EXT = new VkBlendOp(1000148035);
    public static final VkBlendOp VK_BLEND_OP_RED_EXT = new VkBlendOp(1000148043);
    public static final VkBlendOp VK_BLEND_OP_REVERSE_SUBTRACT = new VkBlendOp(2);
    public static final VkBlendOp VK_BLEND_OP_SCREEN_EXT = new VkBlendOp(1000148013);
    public static final VkBlendOp VK_BLEND_OP_SOFTLIGHT_EXT = new VkBlendOp(1000148020);
    public static final VkBlendOp VK_BLEND_OP_SRC_ATOP_EXT = new VkBlendOp(1000148009);
    public static final VkBlendOp VK_BLEND_OP_SRC_EXT = new VkBlendOp(1000148001);
    public static final VkBlendOp VK_BLEND_OP_SRC_IN_EXT = new VkBlendOp(1000148005);
    public static final VkBlendOp VK_BLEND_OP_SRC_OUT_EXT = new VkBlendOp(1000148007);
    public static final VkBlendOp VK_BLEND_OP_SRC_OVER_EXT = new VkBlendOp(1000148003);
    public static final VkBlendOp VK_BLEND_OP_SUBTRACT = new VkBlendOp(1);
    public static final VkBlendOp VK_BLEND_OP_VIVIDLIGHT_EXT = new VkBlendOp(1000148027);
    public static final VkBlendOp VK_BLEND_OP_XOR_EXT = new VkBlendOp(1000148011);
    public static final VkBlendOp VK_BLEND_OP_ZERO_EXT = new VkBlendOp(1000148000);

    public static VkBlendOp fromValue(int value) {
        return switch (value) {
            case 0 -> VK_BLEND_OP_ADD;
            case 1000148045 -> VK_BLEND_OP_BLUE_EXT;
            case 1000148018 -> VK_BLEND_OP_COLORBURN_EXT;
            case 1000148017 -> VK_BLEND_OP_COLORDODGE_EXT;
            case 1000148041 -> VK_BLEND_OP_CONTRAST_EXT;
            case 1000148015 -> VK_BLEND_OP_DARKEN_EXT;
            case 1000148021 -> VK_BLEND_OP_DIFFERENCE_EXT;
            case 1000148010 -> VK_BLEND_OP_DST_ATOP_EXT;
            case 1000148002 -> VK_BLEND_OP_DST_EXT;
            case 1000148006 -> VK_BLEND_OP_DST_IN_EXT;
            case 1000148008 -> VK_BLEND_OP_DST_OUT_EXT;
            case 1000148004 -> VK_BLEND_OP_DST_OVER_EXT;
            case 1000148022 -> VK_BLEND_OP_EXCLUSION_EXT;
            case 1000148044 -> VK_BLEND_OP_GREEN_EXT;
            case 1000148019 -> VK_BLEND_OP_HARDLIGHT_EXT;
            case 1000148030 -> VK_BLEND_OP_HARDMIX_EXT;
            case 1000148033 -> VK_BLEND_OP_HSL_COLOR_EXT;
            case 1000148031 -> VK_BLEND_OP_HSL_HUE_EXT;
            case 1000148034 -> VK_BLEND_OP_HSL_LUMINOSITY_EXT;
            case 1000148032 -> VK_BLEND_OP_HSL_SATURATION_EXT;
            case 1000148023 -> VK_BLEND_OP_INVERT_EXT;
            case 1000148042 -> VK_BLEND_OP_INVERT_OVG_EXT;
            case 1000148024 -> VK_BLEND_OP_INVERT_RGB_EXT;
            case 1000148016 -> VK_BLEND_OP_LIGHTEN_EXT;
            case 1000148026 -> VK_BLEND_OP_LINEARBURN_EXT;
            case 1000148025 -> VK_BLEND_OP_LINEARDODGE_EXT;
            case 1000148028 -> VK_BLEND_OP_LINEARLIGHT_EXT;
            case 4 -> VK_BLEND_OP_MAX;
            case 2147483647 -> VK_BLEND_OP_MAX_ENUM;
            case 3 -> VK_BLEND_OP_MIN;
            case 1000148040 -> VK_BLEND_OP_MINUS_CLAMPED_EXT;
            case 1000148039 -> VK_BLEND_OP_MINUS_EXT;
            case 1000148012 -> VK_BLEND_OP_MULTIPLY_EXT;
            case 1000148014 -> VK_BLEND_OP_OVERLAY_EXT;
            case 1000148029 -> VK_BLEND_OP_PINLIGHT_EXT;
            case 1000148037 -> VK_BLEND_OP_PLUS_CLAMPED_ALPHA_EXT;
            case 1000148036 -> VK_BLEND_OP_PLUS_CLAMPED_EXT;
            case 1000148038 -> VK_BLEND_OP_PLUS_DARKER_EXT;
            case 1000148035 -> VK_BLEND_OP_PLUS_EXT;
            case 1000148043 -> VK_BLEND_OP_RED_EXT;
            case 2 -> VK_BLEND_OP_REVERSE_SUBTRACT;
            case 1000148013 -> VK_BLEND_OP_SCREEN_EXT;
            case 1000148020 -> VK_BLEND_OP_SOFTLIGHT_EXT;
            case 1000148009 -> VK_BLEND_OP_SRC_ATOP_EXT;
            case 1000148001 -> VK_BLEND_OP_SRC_EXT;
            case 1000148005 -> VK_BLEND_OP_SRC_IN_EXT;
            case 1000148007 -> VK_BLEND_OP_SRC_OUT_EXT;
            case 1000148003 -> VK_BLEND_OP_SRC_OVER_EXT;
            case 1 -> VK_BLEND_OP_SUBTRACT;
            case 1000148027 -> VK_BLEND_OP_VIVIDLIGHT_EXT;
            case 1000148011 -> VK_BLEND_OP_XOR_EXT;
            case 1000148000 -> VK_BLEND_OP_ZERO_EXT;
            default -> new VkBlendOp(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
