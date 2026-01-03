package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkShaderFloatControlsIndependence
 * Generated from jextract bindings
 */
public record VkShaderFloatControlsIndependence(int value) {

    public static final VkShaderFloatControlsIndependence VK_SHADER_FLOAT_CONTROLS_INDEPENDENCE_32_BIT_ONLY = new VkShaderFloatControlsIndependence(0);
    public static final VkShaderFloatControlsIndependence VK_SHADER_FLOAT_CONTROLS_INDEPENDENCE_32_BIT_ONLY_KHR = new VkShaderFloatControlsIndependence(0);
    public static final VkShaderFloatControlsIndependence VK_SHADER_FLOAT_CONTROLS_INDEPENDENCE_ALL = new VkShaderFloatControlsIndependence(1);
    public static final VkShaderFloatControlsIndependence VK_SHADER_FLOAT_CONTROLS_INDEPENDENCE_ALL_KHR = new VkShaderFloatControlsIndependence(1);
    public static final VkShaderFloatControlsIndependence VK_SHADER_FLOAT_CONTROLS_INDEPENDENCE_MAX_ENUM = new VkShaderFloatControlsIndependence(2147483647);
    public static final VkShaderFloatControlsIndependence VK_SHADER_FLOAT_CONTROLS_INDEPENDENCE_NONE = new VkShaderFloatControlsIndependence(2);
    public static final VkShaderFloatControlsIndependence VK_SHADER_FLOAT_CONTROLS_INDEPENDENCE_NONE_KHR = new VkShaderFloatControlsIndependence(2);

    public static VkShaderFloatControlsIndependence fromValue(int value) {
        return switch (value) {
            case 0 -> VK_SHADER_FLOAT_CONTROLS_INDEPENDENCE_32_BIT_ONLY;
            case 1 -> VK_SHADER_FLOAT_CONTROLS_INDEPENDENCE_ALL;
            case 2147483647 -> VK_SHADER_FLOAT_CONTROLS_INDEPENDENCE_MAX_ENUM;
            case 2 -> VK_SHADER_FLOAT_CONTROLS_INDEPENDENCE_NONE;
            default -> new VkShaderFloatControlsIndependence(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
