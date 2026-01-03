package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkAccessFlagBits
 * Generated from jextract bindings
 */
public record VkAccessFlagBits(int value) {

    public static final VkAccessFlagBits VK_ACCESS_ACCELERATION_STRUCTURE_READ_BIT_KHR = new VkAccessFlagBits(2097152);
    public static final VkAccessFlagBits VK_ACCESS_ACCELERATION_STRUCTURE_READ_BIT_NV = VK_ACCESS_ACCELERATION_STRUCTURE_READ_BIT_KHR;
    public static final VkAccessFlagBits VK_ACCESS_ACCELERATION_STRUCTURE_WRITE_BIT_KHR = new VkAccessFlagBits(4194304);
    public static final VkAccessFlagBits VK_ACCESS_ACCELERATION_STRUCTURE_WRITE_BIT_NV = VK_ACCESS_ACCELERATION_STRUCTURE_WRITE_BIT_KHR;
    public static final VkAccessFlagBits VK_ACCESS_COLOR_ATTACHMENT_READ_BIT = new VkAccessFlagBits(128);
    public static final VkAccessFlagBits VK_ACCESS_COLOR_ATTACHMENT_READ_NONCOHERENT_BIT_EXT = new VkAccessFlagBits(524288);
    public static final VkAccessFlagBits VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT = new VkAccessFlagBits(256);
    public static final VkAccessFlagBits VK_ACCESS_COMMAND_PREPROCESS_READ_BIT_EXT = new VkAccessFlagBits(131072);
    public static final VkAccessFlagBits VK_ACCESS_COMMAND_PREPROCESS_READ_BIT_NV = VK_ACCESS_COMMAND_PREPROCESS_READ_BIT_EXT;
    public static final VkAccessFlagBits VK_ACCESS_COMMAND_PREPROCESS_WRITE_BIT_EXT = new VkAccessFlagBits(262144);
    public static final VkAccessFlagBits VK_ACCESS_COMMAND_PREPROCESS_WRITE_BIT_NV = VK_ACCESS_COMMAND_PREPROCESS_WRITE_BIT_EXT;
    public static final VkAccessFlagBits VK_ACCESS_CONDITIONAL_RENDERING_READ_BIT_EXT = new VkAccessFlagBits(1048576);
    public static final VkAccessFlagBits VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT = new VkAccessFlagBits(512);
    public static final VkAccessFlagBits VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT = new VkAccessFlagBits(1024);
    public static final VkAccessFlagBits VK_ACCESS_FLAG_BITS_MAX_ENUM = new VkAccessFlagBits(2147483647);
    public static final VkAccessFlagBits VK_ACCESS_FRAGMENT_DENSITY_MAP_READ_BIT_EXT = new VkAccessFlagBits(16777216);
    public static final VkAccessFlagBits VK_ACCESS_FRAGMENT_SHADING_RATE_ATTACHMENT_READ_BIT_KHR = new VkAccessFlagBits(8388608);
    public static final VkAccessFlagBits VK_ACCESS_HOST_READ_BIT = new VkAccessFlagBits(8192);
    public static final VkAccessFlagBits VK_ACCESS_HOST_WRITE_BIT = new VkAccessFlagBits(16384);
    public static final VkAccessFlagBits VK_ACCESS_INDEX_READ_BIT = new VkAccessFlagBits(2);
    public static final VkAccessFlagBits VK_ACCESS_INDIRECT_COMMAND_READ_BIT = new VkAccessFlagBits(1);
    public static final VkAccessFlagBits VK_ACCESS_INPUT_ATTACHMENT_READ_BIT = new VkAccessFlagBits(16);
    public static final VkAccessFlagBits VK_ACCESS_MEMORY_READ_BIT = new VkAccessFlagBits(32768);
    public static final VkAccessFlagBits VK_ACCESS_MEMORY_WRITE_BIT = new VkAccessFlagBits(65536);
    public static final VkAccessFlagBits VK_ACCESS_NONE = new VkAccessFlagBits(0);
    public static final VkAccessFlagBits VK_ACCESS_NONE_KHR = VK_ACCESS_NONE;
    public static final VkAccessFlagBits VK_ACCESS_SHADER_READ_BIT = new VkAccessFlagBits(32);
    public static final VkAccessFlagBits VK_ACCESS_SHADER_WRITE_BIT = new VkAccessFlagBits(64);
    public static final VkAccessFlagBits VK_ACCESS_SHADING_RATE_IMAGE_READ_BIT_NV = VK_ACCESS_FRAGMENT_SHADING_RATE_ATTACHMENT_READ_BIT_KHR;
    public static final VkAccessFlagBits VK_ACCESS_TRANSFER_READ_BIT = new VkAccessFlagBits(2048);
    public static final VkAccessFlagBits VK_ACCESS_TRANSFER_WRITE_BIT = new VkAccessFlagBits(4096);
    public static final VkAccessFlagBits VK_ACCESS_TRANSFORM_FEEDBACK_COUNTER_READ_BIT_EXT = new VkAccessFlagBits(67108864);
    public static final VkAccessFlagBits VK_ACCESS_TRANSFORM_FEEDBACK_COUNTER_WRITE_BIT_EXT = new VkAccessFlagBits(134217728);
    public static final VkAccessFlagBits VK_ACCESS_TRANSFORM_FEEDBACK_WRITE_BIT_EXT = new VkAccessFlagBits(33554432);
    public static final VkAccessFlagBits VK_ACCESS_UNIFORM_READ_BIT = new VkAccessFlagBits(8);
    public static final VkAccessFlagBits VK_ACCESS_VERTEX_ATTRIBUTE_READ_BIT = new VkAccessFlagBits(4);

    public static VkAccessFlagBits fromValue(int value) {
        return switch (value) {
            case 2097152 -> VK_ACCESS_ACCELERATION_STRUCTURE_READ_BIT_NV;
            case 4194304 -> VK_ACCESS_ACCELERATION_STRUCTURE_WRITE_BIT_NV;
            case 128 -> VK_ACCESS_COLOR_ATTACHMENT_READ_BIT;
            case 524288 -> VK_ACCESS_COLOR_ATTACHMENT_READ_NONCOHERENT_BIT_EXT;
            case 256 -> VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
            case 131072 -> VK_ACCESS_COMMAND_PREPROCESS_READ_BIT_NV;
            case 262144 -> VK_ACCESS_COMMAND_PREPROCESS_WRITE_BIT_NV;
            case 1048576 -> VK_ACCESS_CONDITIONAL_RENDERING_READ_BIT_EXT;
            case 512 -> VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT;
            case 1024 -> VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
            case 2147483647 -> VK_ACCESS_FLAG_BITS_MAX_ENUM;
            case 16777216 -> VK_ACCESS_FRAGMENT_DENSITY_MAP_READ_BIT_EXT;
            case 8388608 -> VK_ACCESS_SHADING_RATE_IMAGE_READ_BIT_NV;
            case 8192 -> VK_ACCESS_HOST_READ_BIT;
            case 16384 -> VK_ACCESS_HOST_WRITE_BIT;
            case 2 -> VK_ACCESS_INDEX_READ_BIT;
            case 1 -> VK_ACCESS_INDIRECT_COMMAND_READ_BIT;
            case 16 -> VK_ACCESS_INPUT_ATTACHMENT_READ_BIT;
            case 32768 -> VK_ACCESS_MEMORY_READ_BIT;
            case 65536 -> VK_ACCESS_MEMORY_WRITE_BIT;
            case 0 -> VK_ACCESS_NONE;
            case 32 -> VK_ACCESS_SHADER_READ_BIT;
            case 64 -> VK_ACCESS_SHADER_WRITE_BIT;
            case 2048 -> VK_ACCESS_TRANSFER_READ_BIT;
            case 4096 -> VK_ACCESS_TRANSFER_WRITE_BIT;
            case 67108864 -> VK_ACCESS_TRANSFORM_FEEDBACK_COUNTER_READ_BIT_EXT;
            case 134217728 -> VK_ACCESS_TRANSFORM_FEEDBACK_COUNTER_WRITE_BIT_EXT;
            case 33554432 -> VK_ACCESS_TRANSFORM_FEEDBACK_WRITE_BIT_EXT;
            case 8 -> VK_ACCESS_UNIFORM_READ_BIT;
            case 4 -> VK_ACCESS_VERTEX_ATTRIBUTE_READ_BIT;
            default -> new VkAccessFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
