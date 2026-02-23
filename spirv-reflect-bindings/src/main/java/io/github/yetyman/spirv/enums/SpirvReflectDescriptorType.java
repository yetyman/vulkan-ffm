package io.github.yetyman.spirv.enums;

import java.util.*;

/**
 * Type-safe constants for SpirvReflectDescriptorType
 * Generated from jextract bindings
 */
public record SpirvReflectDescriptorType(int value) {

    public static final SpirvReflectDescriptorType SPV_REFLECT_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR = new SpirvReflectDescriptorType(1000150000);
    public static final SpirvReflectDescriptorType SPV_REFLECT_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER = new SpirvReflectDescriptorType(1);
    public static final SpirvReflectDescriptorType SPV_REFLECT_DESCRIPTOR_TYPE_INPUT_ATTACHMENT = new SpirvReflectDescriptorType(10);
    public static final SpirvReflectDescriptorType SPV_REFLECT_DESCRIPTOR_TYPE_SAMPLED_IMAGE = new SpirvReflectDescriptorType(2);
    public static final SpirvReflectDescriptorType SPV_REFLECT_DESCRIPTOR_TYPE_SAMPLER = new SpirvReflectDescriptorType(0);
    public static final SpirvReflectDescriptorType SPV_REFLECT_DESCRIPTOR_TYPE_STORAGE_BUFFER = new SpirvReflectDescriptorType(7);
    public static final SpirvReflectDescriptorType SPV_REFLECT_DESCRIPTOR_TYPE_STORAGE_BUFFER_DYNAMIC = new SpirvReflectDescriptorType(9);
    public static final SpirvReflectDescriptorType SPV_REFLECT_DESCRIPTOR_TYPE_STORAGE_IMAGE = new SpirvReflectDescriptorType(3);
    public static final SpirvReflectDescriptorType SPV_REFLECT_DESCRIPTOR_TYPE_STORAGE_TEXEL_BUFFER = new SpirvReflectDescriptorType(5);
    public static final SpirvReflectDescriptorType SPV_REFLECT_DESCRIPTOR_TYPE_UNIFORM_BUFFER = new SpirvReflectDescriptorType(6);
    public static final SpirvReflectDescriptorType SPV_REFLECT_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC = new SpirvReflectDescriptorType(8);
    public static final SpirvReflectDescriptorType SPV_REFLECT_DESCRIPTOR_TYPE_UNIFORM_TEXEL_BUFFER = new SpirvReflectDescriptorType(4);

    public static SpirvReflectDescriptorType fromValue(int value) {
        return switch (value) {
            case 1000150000 -> SPV_REFLECT_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR;
            case 1 -> SPV_REFLECT_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
            case 10 -> SPV_REFLECT_DESCRIPTOR_TYPE_INPUT_ATTACHMENT;
            case 2 -> SPV_REFLECT_DESCRIPTOR_TYPE_SAMPLED_IMAGE;
            case 0 -> SPV_REFLECT_DESCRIPTOR_TYPE_SAMPLER;
            case 7 -> SPV_REFLECT_DESCRIPTOR_TYPE_STORAGE_BUFFER;
            case 9 -> SPV_REFLECT_DESCRIPTOR_TYPE_STORAGE_BUFFER_DYNAMIC;
            case 3 -> SPV_REFLECT_DESCRIPTOR_TYPE_STORAGE_IMAGE;
            case 5 -> SPV_REFLECT_DESCRIPTOR_TYPE_STORAGE_TEXEL_BUFFER;
            case 6 -> SPV_REFLECT_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
            case 8 -> SPV_REFLECT_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC;
            case 4 -> SPV_REFLECT_DESCRIPTOR_TYPE_UNIFORM_TEXEL_BUFFER;
            default -> new SpirvReflectDescriptorType(value);
        };
    }
}
