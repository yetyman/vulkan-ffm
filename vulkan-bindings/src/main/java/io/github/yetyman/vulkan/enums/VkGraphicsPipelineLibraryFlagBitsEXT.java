package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkGraphicsPipelineLibraryFlagBitsEXT
 * Generated from jextract bindings
 */
public record VkGraphicsPipelineLibraryFlagBitsEXT(int value) {

    public static final VkGraphicsPipelineLibraryFlagBitsEXT VK_GRAPHICS_PIPELINE_LIBRARY_FLAG_BITS_MAX_ENUM_EXT = new VkGraphicsPipelineLibraryFlagBitsEXT(2147483647);
    public static final VkGraphicsPipelineLibraryFlagBitsEXT VK_GRAPHICS_PIPELINE_LIBRARY_FRAGMENT_OUTPUT_INTERFACE_BIT_EXT = new VkGraphicsPipelineLibraryFlagBitsEXT(8);
    public static final VkGraphicsPipelineLibraryFlagBitsEXT VK_GRAPHICS_PIPELINE_LIBRARY_FRAGMENT_SHADER_BIT_EXT = new VkGraphicsPipelineLibraryFlagBitsEXT(4);
    public static final VkGraphicsPipelineLibraryFlagBitsEXT VK_GRAPHICS_PIPELINE_LIBRARY_PRE_RASTERIZATION_SHADERS_BIT_EXT = new VkGraphicsPipelineLibraryFlagBitsEXT(2);
    public static final VkGraphicsPipelineLibraryFlagBitsEXT VK_GRAPHICS_PIPELINE_LIBRARY_VERTEX_INPUT_INTERFACE_BIT_EXT = new VkGraphicsPipelineLibraryFlagBitsEXT(1);

    public static VkGraphicsPipelineLibraryFlagBitsEXT fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_GRAPHICS_PIPELINE_LIBRARY_FLAG_BITS_MAX_ENUM_EXT;
            case 8 -> VK_GRAPHICS_PIPELINE_LIBRARY_FRAGMENT_OUTPUT_INTERFACE_BIT_EXT;
            case 4 -> VK_GRAPHICS_PIPELINE_LIBRARY_FRAGMENT_SHADER_BIT_EXT;
            case 2 -> VK_GRAPHICS_PIPELINE_LIBRARY_PRE_RASTERIZATION_SHADERS_BIT_EXT;
            case 1 -> VK_GRAPHICS_PIPELINE_LIBRARY_VERTEX_INPUT_INTERFACE_BIT_EXT;
            default -> new VkGraphicsPipelineLibraryFlagBitsEXT(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
