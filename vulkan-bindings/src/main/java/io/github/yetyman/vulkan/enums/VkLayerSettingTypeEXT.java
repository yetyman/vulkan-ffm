package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkLayerSettingTypeEXT
 * Generated from jextract bindings
 */
public record VkLayerSettingTypeEXT(int value) {

    public static final VkLayerSettingTypeEXT VK_LAYER_SETTING_TYPE_BOOL32_EXT = new VkLayerSettingTypeEXT(0);
    public static final VkLayerSettingTypeEXT VK_LAYER_SETTING_TYPE_FLOAT32_EXT = new VkLayerSettingTypeEXT(5);
    public static final VkLayerSettingTypeEXT VK_LAYER_SETTING_TYPE_FLOAT64_EXT = new VkLayerSettingTypeEXT(6);
    public static final VkLayerSettingTypeEXT VK_LAYER_SETTING_TYPE_INT32_EXT = new VkLayerSettingTypeEXT(1);
    public static final VkLayerSettingTypeEXT VK_LAYER_SETTING_TYPE_INT64_EXT = new VkLayerSettingTypeEXT(2);
    public static final VkLayerSettingTypeEXT VK_LAYER_SETTING_TYPE_MAX_ENUM_EXT = new VkLayerSettingTypeEXT(2147483647);
    public static final VkLayerSettingTypeEXT VK_LAYER_SETTING_TYPE_STRING_EXT = new VkLayerSettingTypeEXT(7);
    public static final VkLayerSettingTypeEXT VK_LAYER_SETTING_TYPE_UINT32_EXT = new VkLayerSettingTypeEXT(3);
    public static final VkLayerSettingTypeEXT VK_LAYER_SETTING_TYPE_UINT64_EXT = new VkLayerSettingTypeEXT(4);

    public static VkLayerSettingTypeEXT fromValue(int value) {
        return switch (value) {
            case 0 -> VK_LAYER_SETTING_TYPE_BOOL32_EXT;
            case 5 -> VK_LAYER_SETTING_TYPE_FLOAT32_EXT;
            case 6 -> VK_LAYER_SETTING_TYPE_FLOAT64_EXT;
            case 1 -> VK_LAYER_SETTING_TYPE_INT32_EXT;
            case 2 -> VK_LAYER_SETTING_TYPE_INT64_EXT;
            case 2147483647 -> VK_LAYER_SETTING_TYPE_MAX_ENUM_EXT;
            case 7 -> VK_LAYER_SETTING_TYPE_STRING_EXT;
            case 3 -> VK_LAYER_SETTING_TYPE_UINT32_EXT;
            case 4 -> VK_LAYER_SETTING_TYPE_UINT64_EXT;
            default -> new VkLayerSettingTypeEXT(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
