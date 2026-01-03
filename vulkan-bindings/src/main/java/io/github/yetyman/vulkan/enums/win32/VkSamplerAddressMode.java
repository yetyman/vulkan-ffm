package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkSamplerAddressMode
 * Generated from jextract bindings
 */
public record VkSamplerAddressMode(int value) {

    public static final VkSamplerAddressMode VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_BORDER = new VkSamplerAddressMode(3);
    public static final VkSamplerAddressMode VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE = new VkSamplerAddressMode(2);
    public static final VkSamplerAddressMode VK_SAMPLER_ADDRESS_MODE_MAX_ENUM = new VkSamplerAddressMode(2147483647);
    public static final VkSamplerAddressMode VK_SAMPLER_ADDRESS_MODE_MIRRORED_REPEAT = new VkSamplerAddressMode(1);
    public static final VkSamplerAddressMode VK_SAMPLER_ADDRESS_MODE_MIRROR_CLAMP_TO_EDGE = new VkSamplerAddressMode(4);
    public static final VkSamplerAddressMode VK_SAMPLER_ADDRESS_MODE_MIRROR_CLAMP_TO_EDGE_KHR = new VkSamplerAddressMode(4);
    public static final VkSamplerAddressMode VK_SAMPLER_ADDRESS_MODE_REPEAT = new VkSamplerAddressMode(0);

    public static VkSamplerAddressMode fromValue(int value) {
        return switch (value) {
            case 3 -> VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_BORDER;
            case 2 -> VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
            case 2147483647 -> VK_SAMPLER_ADDRESS_MODE_MAX_ENUM;
            case 1 -> VK_SAMPLER_ADDRESS_MODE_MIRRORED_REPEAT;
            case 4 -> VK_SAMPLER_ADDRESS_MODE_MIRROR_CLAMP_TO_EDGE;
            case 0 -> VK_SAMPLER_ADDRESS_MODE_REPEAT;
            default -> new VkSamplerAddressMode(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
