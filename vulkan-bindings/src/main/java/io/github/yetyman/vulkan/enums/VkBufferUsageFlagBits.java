package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkBufferUsageFlagBits
 * Generated from jextract bindings
 */
public record VkBufferUsageFlagBits(int value) {

    public static final VkBufferUsageFlagBits VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR = new VkBufferUsageFlagBits(524288);
    public static final VkBufferUsageFlagBits VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_STORAGE_BIT_KHR = new VkBufferUsageFlagBits(1048576);
    public static final VkBufferUsageFlagBits VK_BUFFER_USAGE_CONDITIONAL_RENDERING_BIT_EXT = new VkBufferUsageFlagBits(512);
    public static final VkBufferUsageFlagBits VK_BUFFER_USAGE_FLAG_BITS_MAX_ENUM = new VkBufferUsageFlagBits(2147483647);
    public static final VkBufferUsageFlagBits VK_BUFFER_USAGE_INDEX_BUFFER_BIT = new VkBufferUsageFlagBits(64);
    public static final VkBufferUsageFlagBits VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT = new VkBufferUsageFlagBits(256);
    public static final VkBufferUsageFlagBits VK_BUFFER_USAGE_MICROMAP_BUILD_INPUT_READ_ONLY_BIT_EXT = new VkBufferUsageFlagBits(8388608);
    public static final VkBufferUsageFlagBits VK_BUFFER_USAGE_MICROMAP_STORAGE_BIT_EXT = new VkBufferUsageFlagBits(16777216);
    public static final VkBufferUsageFlagBits VK_BUFFER_USAGE_PUSH_DESCRIPTORS_DESCRIPTOR_BUFFER_BIT_EXT = new VkBufferUsageFlagBits(67108864);
    public static final VkBufferUsageFlagBits VK_BUFFER_USAGE_RAY_TRACING_BIT_NV = new VkBufferUsageFlagBits(1024);
    public static final VkBufferUsageFlagBits VK_BUFFER_USAGE_RESOURCE_DESCRIPTOR_BUFFER_BIT_EXT = new VkBufferUsageFlagBits(4194304);
    public static final VkBufferUsageFlagBits VK_BUFFER_USAGE_SAMPLER_DESCRIPTOR_BUFFER_BIT_EXT = new VkBufferUsageFlagBits(2097152);
    public static final VkBufferUsageFlagBits VK_BUFFER_USAGE_SHADER_BINDING_TABLE_BIT_KHR = VK_BUFFER_USAGE_RAY_TRACING_BIT_NV;
    public static final VkBufferUsageFlagBits VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT = new VkBufferUsageFlagBits(131072);
    public static final VkBufferUsageFlagBits VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT_EXT = VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT;
    public static final VkBufferUsageFlagBits VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT_KHR = VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT;
    public static final VkBufferUsageFlagBits VK_BUFFER_USAGE_STORAGE_BUFFER_BIT = new VkBufferUsageFlagBits(32);
    public static final VkBufferUsageFlagBits VK_BUFFER_USAGE_STORAGE_TEXEL_BUFFER_BIT = new VkBufferUsageFlagBits(8);
    public static final VkBufferUsageFlagBits VK_BUFFER_USAGE_TILE_MEMORY_BIT_QCOM = new VkBufferUsageFlagBits(134217728);
    public static final VkBufferUsageFlagBits VK_BUFFER_USAGE_TRANSFER_DST_BIT = new VkBufferUsageFlagBits(2);
    public static final VkBufferUsageFlagBits VK_BUFFER_USAGE_TRANSFER_SRC_BIT = new VkBufferUsageFlagBits(1);
    public static final VkBufferUsageFlagBits VK_BUFFER_USAGE_TRANSFORM_FEEDBACK_BUFFER_BIT_EXT = new VkBufferUsageFlagBits(2048);
    public static final VkBufferUsageFlagBits VK_BUFFER_USAGE_TRANSFORM_FEEDBACK_COUNTER_BUFFER_BIT_EXT = new VkBufferUsageFlagBits(4096);
    public static final VkBufferUsageFlagBits VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT = new VkBufferUsageFlagBits(16);
    public static final VkBufferUsageFlagBits VK_BUFFER_USAGE_UNIFORM_TEXEL_BUFFER_BIT = new VkBufferUsageFlagBits(4);
    public static final VkBufferUsageFlagBits VK_BUFFER_USAGE_VERTEX_BUFFER_BIT = new VkBufferUsageFlagBits(128);
    public static final VkBufferUsageFlagBits VK_BUFFER_USAGE_VIDEO_DECODE_DST_BIT_KHR = new VkBufferUsageFlagBits(16384);
    public static final VkBufferUsageFlagBits VK_BUFFER_USAGE_VIDEO_DECODE_SRC_BIT_KHR = new VkBufferUsageFlagBits(8192);
    public static final VkBufferUsageFlagBits VK_BUFFER_USAGE_VIDEO_ENCODE_DST_BIT_KHR = new VkBufferUsageFlagBits(32768);
    public static final VkBufferUsageFlagBits VK_BUFFER_USAGE_VIDEO_ENCODE_SRC_BIT_KHR = new VkBufferUsageFlagBits(65536);

    public static VkBufferUsageFlagBits fromValue(int value) {
        return switch (value) {
            case 524288 -> VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR;
            case 1048576 -> VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_STORAGE_BIT_KHR;
            case 512 -> VK_BUFFER_USAGE_CONDITIONAL_RENDERING_BIT_EXT;
            case 2147483647 -> VK_BUFFER_USAGE_FLAG_BITS_MAX_ENUM;
            case 64 -> VK_BUFFER_USAGE_INDEX_BUFFER_BIT;
            case 256 -> VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT;
            case 8388608 -> VK_BUFFER_USAGE_MICROMAP_BUILD_INPUT_READ_ONLY_BIT_EXT;
            case 16777216 -> VK_BUFFER_USAGE_MICROMAP_STORAGE_BIT_EXT;
            case 67108864 -> VK_BUFFER_USAGE_PUSH_DESCRIPTORS_DESCRIPTOR_BUFFER_BIT_EXT;
            case 1024 -> VK_BUFFER_USAGE_RAY_TRACING_BIT_NV;
            case 4194304 -> VK_BUFFER_USAGE_RESOURCE_DESCRIPTOR_BUFFER_BIT_EXT;
            case 2097152 -> VK_BUFFER_USAGE_SAMPLER_DESCRIPTOR_BUFFER_BIT_EXT;
            case 131072 -> VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT;
            case 32 -> VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
            case 8 -> VK_BUFFER_USAGE_STORAGE_TEXEL_BUFFER_BIT;
            case 134217728 -> VK_BUFFER_USAGE_TILE_MEMORY_BIT_QCOM;
            case 2 -> VK_BUFFER_USAGE_TRANSFER_DST_BIT;
            case 1 -> VK_BUFFER_USAGE_TRANSFER_SRC_BIT;
            case 2048 -> VK_BUFFER_USAGE_TRANSFORM_FEEDBACK_BUFFER_BIT_EXT;
            case 4096 -> VK_BUFFER_USAGE_TRANSFORM_FEEDBACK_COUNTER_BUFFER_BIT_EXT;
            case 16 -> VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT;
            case 4 -> VK_BUFFER_USAGE_UNIFORM_TEXEL_BUFFER_BIT;
            case 128 -> VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
            case 16384 -> VK_BUFFER_USAGE_VIDEO_DECODE_DST_BIT_KHR;
            case 8192 -> VK_BUFFER_USAGE_VIDEO_DECODE_SRC_BIT_KHR;
            case 32768 -> VK_BUFFER_USAGE_VIDEO_ENCODE_DST_BIT_KHR;
            case 65536 -> VK_BUFFER_USAGE_VIDEO_ENCODE_SRC_BIT_KHR;
            default -> new VkBufferUsageFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
