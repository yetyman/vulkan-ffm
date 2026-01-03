package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkTensorTilingARM
 * Generated from jextract bindings
 */
public record VkTensorTilingARM(int value) {

    public static final VkTensorTilingARM VK_TENSOR_TILING_LINEAR_ARM = new VkTensorTilingARM(1);
    public static final VkTensorTilingARM VK_TENSOR_TILING_MAX_ENUM_ARM = new VkTensorTilingARM(2147483647);
    public static final VkTensorTilingARM VK_TENSOR_TILING_OPTIMAL_ARM = new VkTensorTilingARM(0);

    public static VkTensorTilingARM fromValue(int value) {
        return switch (value) {
            case 1 -> VK_TENSOR_TILING_LINEAR_ARM;
            case 2147483647 -> VK_TENSOR_TILING_MAX_ENUM_ARM;
            case 0 -> VK_TENSOR_TILING_OPTIMAL_ARM;
            default -> new VkTensorTilingARM(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
