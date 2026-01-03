package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkColorSpaceKHR
 * Generated from jextract bindings
 */
public record VkColorSpaceKHR(int value) {

    public static final VkColorSpaceKHR VK_COLORSPACE_SRGB_NONLINEAR_KHR = new VkColorSpaceKHR(0);
    public static final VkColorSpaceKHR VK_COLOR_SPACE_ADOBERGB_LINEAR_EXT = new VkColorSpaceKHR(1000104011);
    public static final VkColorSpaceKHR VK_COLOR_SPACE_ADOBERGB_NONLINEAR_EXT = new VkColorSpaceKHR(1000104012);
    public static final VkColorSpaceKHR VK_COLOR_SPACE_BT2020_LINEAR_EXT = new VkColorSpaceKHR(1000104007);
    public static final VkColorSpaceKHR VK_COLOR_SPACE_BT709_LINEAR_EXT = new VkColorSpaceKHR(1000104005);
    public static final VkColorSpaceKHR VK_COLOR_SPACE_BT709_NONLINEAR_EXT = new VkColorSpaceKHR(1000104006);
    public static final VkColorSpaceKHR VK_COLOR_SPACE_DCI_P3_LINEAR_EXT = new VkColorSpaceKHR(1000104003);
    public static final VkColorSpaceKHR VK_COLOR_SPACE_DCI_P3_NONLINEAR_EXT = new VkColorSpaceKHR(1000104004);
    public static final VkColorSpaceKHR VK_COLOR_SPACE_DISPLAY_NATIVE_AMD = new VkColorSpaceKHR(1000213000);
    public static final VkColorSpaceKHR VK_COLOR_SPACE_DISPLAY_P3_LINEAR_EXT = VK_COLOR_SPACE_DCI_P3_LINEAR_EXT;
    public static final VkColorSpaceKHR VK_COLOR_SPACE_DISPLAY_P3_NONLINEAR_EXT = new VkColorSpaceKHR(1000104001);
    public static final VkColorSpaceKHR VK_COLOR_SPACE_DOLBYVISION_EXT = new VkColorSpaceKHR(1000104009);
    public static final VkColorSpaceKHR VK_COLOR_SPACE_EXTENDED_SRGB_LINEAR_EXT = new VkColorSpaceKHR(1000104002);
    public static final VkColorSpaceKHR VK_COLOR_SPACE_EXTENDED_SRGB_NONLINEAR_EXT = new VkColorSpaceKHR(1000104014);
    public static final VkColorSpaceKHR VK_COLOR_SPACE_HDR10_HLG_EXT = new VkColorSpaceKHR(1000104010);
    public static final VkColorSpaceKHR VK_COLOR_SPACE_HDR10_ST2084_EXT = new VkColorSpaceKHR(1000104008);
    public static final VkColorSpaceKHR VK_COLOR_SPACE_MAX_ENUM_KHR = new VkColorSpaceKHR(2147483647);
    public static final VkColorSpaceKHR VK_COLOR_SPACE_PASS_THROUGH_EXT = new VkColorSpaceKHR(1000104013);
    public static final VkColorSpaceKHR VK_COLOR_SPACE_SRGB_NONLINEAR_KHR = VK_COLORSPACE_SRGB_NONLINEAR_KHR;

    public static VkColorSpaceKHR fromValue(int value) {
        return switch (value) {
            case 0 -> VK_COLORSPACE_SRGB_NONLINEAR_KHR;
            case 1000104011 -> VK_COLOR_SPACE_ADOBERGB_LINEAR_EXT;
            case 1000104012 -> VK_COLOR_SPACE_ADOBERGB_NONLINEAR_EXT;
            case 1000104007 -> VK_COLOR_SPACE_BT2020_LINEAR_EXT;
            case 1000104005 -> VK_COLOR_SPACE_BT709_LINEAR_EXT;
            case 1000104006 -> VK_COLOR_SPACE_BT709_NONLINEAR_EXT;
            case 1000104003 -> VK_COLOR_SPACE_DCI_P3_LINEAR_EXT;
            case 1000104004 -> VK_COLOR_SPACE_DCI_P3_NONLINEAR_EXT;
            case 1000213000 -> VK_COLOR_SPACE_DISPLAY_NATIVE_AMD;
            case 1000104001 -> VK_COLOR_SPACE_DISPLAY_P3_NONLINEAR_EXT;
            case 1000104009 -> VK_COLOR_SPACE_DOLBYVISION_EXT;
            case 1000104002 -> VK_COLOR_SPACE_EXTENDED_SRGB_LINEAR_EXT;
            case 1000104014 -> VK_COLOR_SPACE_EXTENDED_SRGB_NONLINEAR_EXT;
            case 1000104010 -> VK_COLOR_SPACE_HDR10_HLG_EXT;
            case 1000104008 -> VK_COLOR_SPACE_HDR10_ST2084_EXT;
            case 2147483647 -> VK_COLOR_SPACE_MAX_ENUM_KHR;
            case 1000104013 -> VK_COLOR_SPACE_PASS_THROUGH_EXT;
            default -> new VkColorSpaceKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
