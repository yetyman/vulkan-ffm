package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkDescriptorType
 * Generated from jextract bindings
 */
public record VkDescriptorType(int value) {

    public static final VkDescriptorType VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR = new VkDescriptorType(1000150000);
    public static final VkDescriptorType VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_NV = new VkDescriptorType(1000165000);
    public static final VkDescriptorType VK_DESCRIPTOR_TYPE_BLOCK_MATCH_IMAGE_QCOM = new VkDescriptorType(1000440001);
    public static final VkDescriptorType VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER = new VkDescriptorType(1);
    public static final VkDescriptorType VK_DESCRIPTOR_TYPE_INLINE_UNIFORM_BLOCK = new VkDescriptorType(1000138000);
    public static final VkDescriptorType VK_DESCRIPTOR_TYPE_INLINE_UNIFORM_BLOCK_EXT = new VkDescriptorType(1000138000);
    public static final VkDescriptorType VK_DESCRIPTOR_TYPE_INPUT_ATTACHMENT = new VkDescriptorType(10);
    public static final VkDescriptorType VK_DESCRIPTOR_TYPE_MAX_ENUM = new VkDescriptorType(2147483647);
    public static final VkDescriptorType VK_DESCRIPTOR_TYPE_MUTABLE_EXT = new VkDescriptorType(1000351000);
    public static final VkDescriptorType VK_DESCRIPTOR_TYPE_MUTABLE_VALVE = new VkDescriptorType(1000351000);
    public static final VkDescriptorType VK_DESCRIPTOR_TYPE_PARTITIONED_ACCELERATION_STRUCTURE_NV = new VkDescriptorType(1000570000);
    public static final VkDescriptorType VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE = new VkDescriptorType(2);
    public static final VkDescriptorType VK_DESCRIPTOR_TYPE_SAMPLER = new VkDescriptorType(0);
    public static final VkDescriptorType VK_DESCRIPTOR_TYPE_SAMPLE_WEIGHT_IMAGE_QCOM = new VkDescriptorType(1000440000);
    public static final VkDescriptorType VK_DESCRIPTOR_TYPE_STORAGE_BUFFER = new VkDescriptorType(7);
    public static final VkDescriptorType VK_DESCRIPTOR_TYPE_STORAGE_BUFFER_DYNAMIC = new VkDescriptorType(9);
    public static final VkDescriptorType VK_DESCRIPTOR_TYPE_STORAGE_IMAGE = new VkDescriptorType(3);
    public static final VkDescriptorType VK_DESCRIPTOR_TYPE_STORAGE_TEXEL_BUFFER = new VkDescriptorType(5);
    public static final VkDescriptorType VK_DESCRIPTOR_TYPE_TENSOR_ARM = new VkDescriptorType(1000460000);
    public static final VkDescriptorType VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER = new VkDescriptorType(6);
    public static final VkDescriptorType VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC = new VkDescriptorType(8);
    public static final VkDescriptorType VK_DESCRIPTOR_TYPE_UNIFORM_TEXEL_BUFFER = new VkDescriptorType(4);

    public static VkDescriptorType fromValue(int value) {
        return switch (value) {
            case 1000150000 -> VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR;
            case 1000165000 -> VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_NV;
            case 1000440001 -> VK_DESCRIPTOR_TYPE_BLOCK_MATCH_IMAGE_QCOM;
            case 1 -> VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
            case 1000138000 -> VK_DESCRIPTOR_TYPE_INLINE_UNIFORM_BLOCK;
            case 10 -> VK_DESCRIPTOR_TYPE_INPUT_ATTACHMENT;
            case 2147483647 -> VK_DESCRIPTOR_TYPE_MAX_ENUM;
            case 1000351000 -> VK_DESCRIPTOR_TYPE_MUTABLE_VALVE;
            case 1000570000 -> VK_DESCRIPTOR_TYPE_PARTITIONED_ACCELERATION_STRUCTURE_NV;
            case 2 -> VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE;
            case 0 -> VK_DESCRIPTOR_TYPE_SAMPLER;
            case 1000440000 -> VK_DESCRIPTOR_TYPE_SAMPLE_WEIGHT_IMAGE_QCOM;
            case 7 -> VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
            case 9 -> VK_DESCRIPTOR_TYPE_STORAGE_BUFFER_DYNAMIC;
            case 3 -> VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;
            case 5 -> VK_DESCRIPTOR_TYPE_STORAGE_TEXEL_BUFFER;
            case 1000460000 -> VK_DESCRIPTOR_TYPE_TENSOR_ARM;
            case 6 -> VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
            case 8 -> VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC;
            case 4 -> VK_DESCRIPTOR_TYPE_UNIFORM_TEXEL_BUFFER;
            default -> new VkDescriptorType(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
