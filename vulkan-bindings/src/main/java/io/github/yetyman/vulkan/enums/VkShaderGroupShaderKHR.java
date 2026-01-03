package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkShaderGroupShaderKHR
 * Generated from jextract bindings
 */
public record VkShaderGroupShaderKHR(int value) {

    public static final VkShaderGroupShaderKHR VK_SHADER_GROUP_SHADER_ANY_HIT_KHR = new VkShaderGroupShaderKHR(2);
    public static final VkShaderGroupShaderKHR VK_SHADER_GROUP_SHADER_CLOSEST_HIT_KHR = new VkShaderGroupShaderKHR(1);
    public static final VkShaderGroupShaderKHR VK_SHADER_GROUP_SHADER_GENERAL_KHR = new VkShaderGroupShaderKHR(0);
    public static final VkShaderGroupShaderKHR VK_SHADER_GROUP_SHADER_INTERSECTION_KHR = new VkShaderGroupShaderKHR(3);
    public static final VkShaderGroupShaderKHR VK_SHADER_GROUP_SHADER_MAX_ENUM_KHR = new VkShaderGroupShaderKHR(2147483647);

    public static VkShaderGroupShaderKHR fromValue(int value) {
        return switch (value) {
            case 2 -> VK_SHADER_GROUP_SHADER_ANY_HIT_KHR;
            case 1 -> VK_SHADER_GROUP_SHADER_CLOSEST_HIT_KHR;
            case 0 -> VK_SHADER_GROUP_SHADER_GENERAL_KHR;
            case 3 -> VK_SHADER_GROUP_SHADER_INTERSECTION_KHR;
            case 2147483647 -> VK_SHADER_GROUP_SHADER_MAX_ENUM_KHR;
            default -> new VkShaderGroupShaderKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
