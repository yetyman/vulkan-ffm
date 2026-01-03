package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkBufferCreateFlagBits
 * Generated from jextract bindings
 */
public record VkBufferCreateFlagBits(int value) {

    public static final VkBufferCreateFlagBits VK_BUFFER_CREATE_DESCRIPTOR_BUFFER_CAPTURE_REPLAY_BIT_EXT = new VkBufferCreateFlagBits(32);
    public static final VkBufferCreateFlagBits VK_BUFFER_CREATE_DEVICE_ADDRESS_CAPTURE_REPLAY_BIT = new VkBufferCreateFlagBits(16);
    public static final VkBufferCreateFlagBits VK_BUFFER_CREATE_DEVICE_ADDRESS_CAPTURE_REPLAY_BIT_EXT = VK_BUFFER_CREATE_DEVICE_ADDRESS_CAPTURE_REPLAY_BIT;
    public static final VkBufferCreateFlagBits VK_BUFFER_CREATE_DEVICE_ADDRESS_CAPTURE_REPLAY_BIT_KHR = VK_BUFFER_CREATE_DEVICE_ADDRESS_CAPTURE_REPLAY_BIT;
    public static final VkBufferCreateFlagBits VK_BUFFER_CREATE_FLAG_BITS_MAX_ENUM = new VkBufferCreateFlagBits(2147483647);
    public static final VkBufferCreateFlagBits VK_BUFFER_CREATE_PROTECTED_BIT = new VkBufferCreateFlagBits(8);
    public static final VkBufferCreateFlagBits VK_BUFFER_CREATE_SPARSE_ALIASED_BIT = new VkBufferCreateFlagBits(4);
    public static final VkBufferCreateFlagBits VK_BUFFER_CREATE_SPARSE_BINDING_BIT = new VkBufferCreateFlagBits(1);
    public static final VkBufferCreateFlagBits VK_BUFFER_CREATE_SPARSE_RESIDENCY_BIT = new VkBufferCreateFlagBits(2);
    public static final VkBufferCreateFlagBits VK_BUFFER_CREATE_VIDEO_PROFILE_INDEPENDENT_BIT_KHR = new VkBufferCreateFlagBits(64);

    public static VkBufferCreateFlagBits fromValue(int value) {
        return switch (value) {
            case 32 -> VK_BUFFER_CREATE_DESCRIPTOR_BUFFER_CAPTURE_REPLAY_BIT_EXT;
            case 16 -> VK_BUFFER_CREATE_DEVICE_ADDRESS_CAPTURE_REPLAY_BIT;
            case 2147483647 -> VK_BUFFER_CREATE_FLAG_BITS_MAX_ENUM;
            case 8 -> VK_BUFFER_CREATE_PROTECTED_BIT;
            case 4 -> VK_BUFFER_CREATE_SPARSE_ALIASED_BIT;
            case 1 -> VK_BUFFER_CREATE_SPARSE_BINDING_BIT;
            case 2 -> VK_BUFFER_CREATE_SPARSE_RESIDENCY_BIT;
            case 64 -> VK_BUFFER_CREATE_VIDEO_PROFILE_INDEPENDENT_BIT_KHR;
            default -> new VkBufferCreateFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
