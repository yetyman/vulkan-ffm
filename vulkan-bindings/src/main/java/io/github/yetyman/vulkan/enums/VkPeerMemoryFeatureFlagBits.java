package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkPeerMemoryFeatureFlagBits
 * Generated from jextract bindings
 */
public record VkPeerMemoryFeatureFlagBits(int value) {

    public static final VkPeerMemoryFeatureFlagBits VK_PEER_MEMORY_FEATURE_COPY_DST_BIT = new VkPeerMemoryFeatureFlagBits(2);
    public static final VkPeerMemoryFeatureFlagBits VK_PEER_MEMORY_FEATURE_COPY_DST_BIT_KHR = VK_PEER_MEMORY_FEATURE_COPY_DST_BIT;
    public static final VkPeerMemoryFeatureFlagBits VK_PEER_MEMORY_FEATURE_COPY_SRC_BIT = new VkPeerMemoryFeatureFlagBits(1);
    public static final VkPeerMemoryFeatureFlagBits VK_PEER_MEMORY_FEATURE_COPY_SRC_BIT_KHR = VK_PEER_MEMORY_FEATURE_COPY_SRC_BIT;
    public static final VkPeerMemoryFeatureFlagBits VK_PEER_MEMORY_FEATURE_FLAG_BITS_MAX_ENUM = new VkPeerMemoryFeatureFlagBits(2147483647);
    public static final VkPeerMemoryFeatureFlagBits VK_PEER_MEMORY_FEATURE_GENERIC_DST_BIT = new VkPeerMemoryFeatureFlagBits(8);
    public static final VkPeerMemoryFeatureFlagBits VK_PEER_MEMORY_FEATURE_GENERIC_DST_BIT_KHR = VK_PEER_MEMORY_FEATURE_GENERIC_DST_BIT;
    public static final VkPeerMemoryFeatureFlagBits VK_PEER_MEMORY_FEATURE_GENERIC_SRC_BIT = new VkPeerMemoryFeatureFlagBits(4);
    public static final VkPeerMemoryFeatureFlagBits VK_PEER_MEMORY_FEATURE_GENERIC_SRC_BIT_KHR = VK_PEER_MEMORY_FEATURE_GENERIC_SRC_BIT;

    public static VkPeerMemoryFeatureFlagBits fromValue(int value) {
        return switch (value) {
            case 2 -> VK_PEER_MEMORY_FEATURE_COPY_DST_BIT;
            case 1 -> VK_PEER_MEMORY_FEATURE_COPY_SRC_BIT;
            case 2147483647 -> VK_PEER_MEMORY_FEATURE_FLAG_BITS_MAX_ENUM;
            case 8 -> VK_PEER_MEMORY_FEATURE_GENERIC_DST_BIT;
            case 4 -> VK_PEER_MEMORY_FEATURE_GENERIC_SRC_BIT;
            default -> new VkPeerMemoryFeatureFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
