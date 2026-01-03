package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkPointClippingBehavior
 * Generated from jextract bindings
 */
public record VkPointClippingBehavior(int value) {

    public static final VkPointClippingBehavior VK_POINT_CLIPPING_BEHAVIOR_ALL_CLIP_PLANES = new VkPointClippingBehavior(0);
    public static final VkPointClippingBehavior VK_POINT_CLIPPING_BEHAVIOR_ALL_CLIP_PLANES_KHR = VK_POINT_CLIPPING_BEHAVIOR_ALL_CLIP_PLANES;
    public static final VkPointClippingBehavior VK_POINT_CLIPPING_BEHAVIOR_MAX_ENUM = new VkPointClippingBehavior(2147483647);
    public static final VkPointClippingBehavior VK_POINT_CLIPPING_BEHAVIOR_USER_CLIP_PLANES_ONLY = new VkPointClippingBehavior(1);
    public static final VkPointClippingBehavior VK_POINT_CLIPPING_BEHAVIOR_USER_CLIP_PLANES_ONLY_KHR = VK_POINT_CLIPPING_BEHAVIOR_USER_CLIP_PLANES_ONLY;

    public static VkPointClippingBehavior fromValue(int value) {
        return switch (value) {
            case 0 -> VK_POINT_CLIPPING_BEHAVIOR_ALL_CLIP_PLANES;
            case 2147483647 -> VK_POINT_CLIPPING_BEHAVIOR_MAX_ENUM;
            case 1 -> VK_POINT_CLIPPING_BEHAVIOR_USER_CLIP_PLANES_ONLY;
            default -> new VkPointClippingBehavior(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
