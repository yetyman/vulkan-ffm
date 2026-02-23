package io.github.yetyman.spirv.enums;

import java.util.*;

/**
 * Type-safe constants for SpirvReflectShaderStage
 * Generated from jextract bindings
 */
public record SpirvReflectShaderStage(int value) {

    public static final SpirvReflectShaderStage SPV_REFLECT_SHADER_STAGE_COMPUTE_BIT = new SpirvReflectShaderStage(32);
    public static final SpirvReflectShaderStage SPV_REFLECT_SHADER_STAGE_FRAGMENT_BIT = new SpirvReflectShaderStage(16);
    public static final SpirvReflectShaderStage SPV_REFLECT_SHADER_STAGE_GEOMETRY_BIT = new SpirvReflectShaderStage(8);
    public static final SpirvReflectShaderStage SPV_REFLECT_SHADER_STAGE_TESSELLATION_CONTROL_BIT = new SpirvReflectShaderStage(2);
    public static final SpirvReflectShaderStage SPV_REFLECT_SHADER_STAGE_TESSELLATION_EVALUATION_BIT = new SpirvReflectShaderStage(4);
    public static final SpirvReflectShaderStage SPV_REFLECT_SHADER_STAGE_VERTEX_BIT = new SpirvReflectShaderStage(1);

    public static SpirvReflectShaderStage fromValue(int value) {
        return switch (value) {
            case 32 -> SPV_REFLECT_SHADER_STAGE_COMPUTE_BIT;
            case 16 -> SPV_REFLECT_SHADER_STAGE_FRAGMENT_BIT;
            case 8 -> SPV_REFLECT_SHADER_STAGE_GEOMETRY_BIT;
            case 2 -> SPV_REFLECT_SHADER_STAGE_TESSELLATION_CONTROL_BIT;
            case 4 -> SPV_REFLECT_SHADER_STAGE_TESSELLATION_EVALUATION_BIT;
            case 1 -> SPV_REFLECT_SHADER_STAGE_VERTEX_BIT;
            default -> new SpirvReflectShaderStage(value);
        };
    }
}
