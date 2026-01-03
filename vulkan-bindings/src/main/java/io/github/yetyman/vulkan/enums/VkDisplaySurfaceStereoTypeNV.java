package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkDisplaySurfaceStereoTypeNV
 * Generated from jextract bindings
 */
public record VkDisplaySurfaceStereoTypeNV(int value) {

    public static final VkDisplaySurfaceStereoTypeNV VK_DISPLAY_SURFACE_STEREO_TYPE_HDMI_3D_NV = new VkDisplaySurfaceStereoTypeNV(2);
    public static final VkDisplaySurfaceStereoTypeNV VK_DISPLAY_SURFACE_STEREO_TYPE_INBAND_DISPLAYPORT_NV = new VkDisplaySurfaceStereoTypeNV(3);
    public static final VkDisplaySurfaceStereoTypeNV VK_DISPLAY_SURFACE_STEREO_TYPE_MAX_ENUM_NV = new VkDisplaySurfaceStereoTypeNV(2147483647);
    public static final VkDisplaySurfaceStereoTypeNV VK_DISPLAY_SURFACE_STEREO_TYPE_NONE_NV = new VkDisplaySurfaceStereoTypeNV(0);
    public static final VkDisplaySurfaceStereoTypeNV VK_DISPLAY_SURFACE_STEREO_TYPE_ONBOARD_DIN_NV = new VkDisplaySurfaceStereoTypeNV(1);

    public static VkDisplaySurfaceStereoTypeNV fromValue(int value) {
        return switch (value) {
            case 2 -> VK_DISPLAY_SURFACE_STEREO_TYPE_HDMI_3D_NV;
            case 3 -> VK_DISPLAY_SURFACE_STEREO_TYPE_INBAND_DISPLAYPORT_NV;
            case 2147483647 -> VK_DISPLAY_SURFACE_STEREO_TYPE_MAX_ENUM_NV;
            case 0 -> VK_DISPLAY_SURFACE_STEREO_TYPE_NONE_NV;
            case 1 -> VK_DISPLAY_SURFACE_STEREO_TYPE_ONBOARD_DIN_NV;
            default -> new VkDisplaySurfaceStereoTypeNV(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
