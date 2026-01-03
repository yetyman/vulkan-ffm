package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkQueueFlagBits
 * Generated from jextract bindings
 */
public record VkQueueFlagBits(int value) {

    public static final VkQueueFlagBits VK_QUEUE_COMPUTE_BIT = new VkQueueFlagBits(2);
    public static final VkQueueFlagBits VK_QUEUE_DATA_GRAPH_BIT_ARM = new VkQueueFlagBits(1024);
    public static final VkQueueFlagBits VK_QUEUE_FLAG_BITS_MAX_ENUM = new VkQueueFlagBits(2147483647);
    public static final VkQueueFlagBits VK_QUEUE_GRAPHICS_BIT = new VkQueueFlagBits(1);
    public static final VkQueueFlagBits VK_QUEUE_OPTICAL_FLOW_BIT_NV = new VkQueueFlagBits(256);
    public static final VkQueueFlagBits VK_QUEUE_PROTECTED_BIT = new VkQueueFlagBits(16);
    public static final VkQueueFlagBits VK_QUEUE_SPARSE_BINDING_BIT = new VkQueueFlagBits(8);
    public static final VkQueueFlagBits VK_QUEUE_TRANSFER_BIT = new VkQueueFlagBits(4);
    public static final VkQueueFlagBits VK_QUEUE_VIDEO_DECODE_BIT_KHR = new VkQueueFlagBits(32);
    public static final VkQueueFlagBits VK_QUEUE_VIDEO_ENCODE_BIT_KHR = new VkQueueFlagBits(64);

    public static VkQueueFlagBits fromValue(int value) {
        return switch (value) {
            case 2 -> VK_QUEUE_COMPUTE_BIT;
            case 1024 -> VK_QUEUE_DATA_GRAPH_BIT_ARM;
            case 2147483647 -> VK_QUEUE_FLAG_BITS_MAX_ENUM;
            case 1 -> VK_QUEUE_GRAPHICS_BIT;
            case 256 -> VK_QUEUE_OPTICAL_FLOW_BIT_NV;
            case 16 -> VK_QUEUE_PROTECTED_BIT;
            case 8 -> VK_QUEUE_SPARSE_BINDING_BIT;
            case 4 -> VK_QUEUE_TRANSFER_BIT;
            case 32 -> VK_QUEUE_VIDEO_DECODE_BIT_KHR;
            case 64 -> VK_QUEUE_VIDEO_ENCODE_BIT_KHR;
            default -> new VkQueueFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
