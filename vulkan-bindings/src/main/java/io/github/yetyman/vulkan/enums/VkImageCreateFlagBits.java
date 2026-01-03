package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkImageCreateFlagBits
 * Generated from jextract bindings
 */
public record VkImageCreateFlagBits(int value) {

    public static final VkImageCreateFlagBits VK_IMAGE_CREATE_2D_ARRAY_COMPATIBLE_BIT = new VkImageCreateFlagBits(32);
    public static final VkImageCreateFlagBits VK_IMAGE_CREATE_2D_ARRAY_COMPATIBLE_BIT_KHR = VK_IMAGE_CREATE_2D_ARRAY_COMPATIBLE_BIT;
    public static final VkImageCreateFlagBits VK_IMAGE_CREATE_2D_VIEW_COMPATIBLE_BIT_EXT = new VkImageCreateFlagBits(131072);
    public static final VkImageCreateFlagBits VK_IMAGE_CREATE_ALIAS_BIT = new VkImageCreateFlagBits(1024);
    public static final VkImageCreateFlagBits VK_IMAGE_CREATE_ALIAS_BIT_KHR = VK_IMAGE_CREATE_ALIAS_BIT;
    public static final VkImageCreateFlagBits VK_IMAGE_CREATE_BLOCK_TEXEL_VIEW_COMPATIBLE_BIT = new VkImageCreateFlagBits(128);
    public static final VkImageCreateFlagBits VK_IMAGE_CREATE_BLOCK_TEXEL_VIEW_COMPATIBLE_BIT_KHR = VK_IMAGE_CREATE_BLOCK_TEXEL_VIEW_COMPATIBLE_BIT;
    public static final VkImageCreateFlagBits VK_IMAGE_CREATE_CORNER_SAMPLED_BIT_NV = new VkImageCreateFlagBits(8192);
    public static final VkImageCreateFlagBits VK_IMAGE_CREATE_CUBE_COMPATIBLE_BIT = new VkImageCreateFlagBits(16);
    public static final VkImageCreateFlagBits VK_IMAGE_CREATE_DESCRIPTOR_BUFFER_CAPTURE_REPLAY_BIT_EXT = new VkImageCreateFlagBits(65536);
    public static final VkImageCreateFlagBits VK_IMAGE_CREATE_DISJOINT_BIT = new VkImageCreateFlagBits(512);
    public static final VkImageCreateFlagBits VK_IMAGE_CREATE_DISJOINT_BIT_KHR = VK_IMAGE_CREATE_DISJOINT_BIT;
    public static final VkImageCreateFlagBits VK_IMAGE_CREATE_EXTENDED_USAGE_BIT = new VkImageCreateFlagBits(256);
    public static final VkImageCreateFlagBits VK_IMAGE_CREATE_EXTENDED_USAGE_BIT_KHR = VK_IMAGE_CREATE_EXTENDED_USAGE_BIT;
    public static final VkImageCreateFlagBits VK_IMAGE_CREATE_FLAG_BITS_MAX_ENUM = new VkImageCreateFlagBits(2147483647);
    public static final VkImageCreateFlagBits VK_IMAGE_CREATE_FRAGMENT_DENSITY_MAP_OFFSET_BIT_EXT = new VkImageCreateFlagBits(32768);
    public static final VkImageCreateFlagBits VK_IMAGE_CREATE_FRAGMENT_DENSITY_MAP_OFFSET_BIT_QCOM = VK_IMAGE_CREATE_FRAGMENT_DENSITY_MAP_OFFSET_BIT_EXT;
    public static final VkImageCreateFlagBits VK_IMAGE_CREATE_MULTISAMPLED_RENDER_TO_SINGLE_SAMPLED_BIT_EXT = new VkImageCreateFlagBits(262144);
    public static final VkImageCreateFlagBits VK_IMAGE_CREATE_MUTABLE_FORMAT_BIT = new VkImageCreateFlagBits(8);
    public static final VkImageCreateFlagBits VK_IMAGE_CREATE_PROTECTED_BIT = new VkImageCreateFlagBits(2048);
    public static final VkImageCreateFlagBits VK_IMAGE_CREATE_SAMPLE_LOCATIONS_COMPATIBLE_DEPTH_BIT_EXT = new VkImageCreateFlagBits(4096);
    public static final VkImageCreateFlagBits VK_IMAGE_CREATE_SPARSE_ALIASED_BIT = new VkImageCreateFlagBits(4);
    public static final VkImageCreateFlagBits VK_IMAGE_CREATE_SPARSE_BINDING_BIT = new VkImageCreateFlagBits(1);
    public static final VkImageCreateFlagBits VK_IMAGE_CREATE_SPARSE_RESIDENCY_BIT = new VkImageCreateFlagBits(2);
    public static final VkImageCreateFlagBits VK_IMAGE_CREATE_SPLIT_INSTANCE_BIND_REGIONS_BIT = new VkImageCreateFlagBits(64);
    public static final VkImageCreateFlagBits VK_IMAGE_CREATE_SPLIT_INSTANCE_BIND_REGIONS_BIT_KHR = VK_IMAGE_CREATE_SPLIT_INSTANCE_BIND_REGIONS_BIT;
    public static final VkImageCreateFlagBits VK_IMAGE_CREATE_SUBSAMPLED_BIT_EXT = new VkImageCreateFlagBits(16384);
    public static final VkImageCreateFlagBits VK_IMAGE_CREATE_VIDEO_PROFILE_INDEPENDENT_BIT_KHR = new VkImageCreateFlagBits(1048576);

    public static VkImageCreateFlagBits fromValue(int value) {
        return switch (value) {
            case 32 -> VK_IMAGE_CREATE_2D_ARRAY_COMPATIBLE_BIT;
            case 131072 -> VK_IMAGE_CREATE_2D_VIEW_COMPATIBLE_BIT_EXT;
            case 1024 -> VK_IMAGE_CREATE_ALIAS_BIT;
            case 128 -> VK_IMAGE_CREATE_BLOCK_TEXEL_VIEW_COMPATIBLE_BIT;
            case 8192 -> VK_IMAGE_CREATE_CORNER_SAMPLED_BIT_NV;
            case 16 -> VK_IMAGE_CREATE_CUBE_COMPATIBLE_BIT;
            case 65536 -> VK_IMAGE_CREATE_DESCRIPTOR_BUFFER_CAPTURE_REPLAY_BIT_EXT;
            case 512 -> VK_IMAGE_CREATE_DISJOINT_BIT;
            case 256 -> VK_IMAGE_CREATE_EXTENDED_USAGE_BIT;
            case 2147483647 -> VK_IMAGE_CREATE_FLAG_BITS_MAX_ENUM;
            case 32768 -> VK_IMAGE_CREATE_FRAGMENT_DENSITY_MAP_OFFSET_BIT_QCOM;
            case 262144 -> VK_IMAGE_CREATE_MULTISAMPLED_RENDER_TO_SINGLE_SAMPLED_BIT_EXT;
            case 8 -> VK_IMAGE_CREATE_MUTABLE_FORMAT_BIT;
            case 2048 -> VK_IMAGE_CREATE_PROTECTED_BIT;
            case 4096 -> VK_IMAGE_CREATE_SAMPLE_LOCATIONS_COMPATIBLE_DEPTH_BIT_EXT;
            case 4 -> VK_IMAGE_CREATE_SPARSE_ALIASED_BIT;
            case 1 -> VK_IMAGE_CREATE_SPARSE_BINDING_BIT;
            case 2 -> VK_IMAGE_CREATE_SPARSE_RESIDENCY_BIT;
            case 64 -> VK_IMAGE_CREATE_SPLIT_INSTANCE_BIND_REGIONS_BIT;
            case 16384 -> VK_IMAGE_CREATE_SUBSAMPLED_BIT_EXT;
            case 1048576 -> VK_IMAGE_CREATE_VIDEO_PROFILE_INDEPENDENT_BIT_KHR;
            default -> new VkImageCreateFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
