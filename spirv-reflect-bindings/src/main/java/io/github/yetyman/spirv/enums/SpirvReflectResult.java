package io.github.yetyman.spirv.enums;

import java.util.*;

/**
 * Type-safe constants for SpirvReflectResult
 * Generated from jextract bindings
 */
public record SpirvReflectResult(int value) {

    public static final SpirvReflectResult SPV_REFLECT_RESULT_ERROR_ALLOC_FAILED = new SpirvReflectResult(-2);
    public static final SpirvReflectResult SPV_REFLECT_RESULT_ERROR_COUNT_MISMATCH = new SpirvReflectResult(-6);
    public static final SpirvReflectResult SPV_REFLECT_RESULT_ERROR_ELEMENT_NOT_FOUND = new SpirvReflectResult(-7);
    public static final SpirvReflectResult SPV_REFLECT_RESULT_ERROR_INTERNAL_ERROR = new SpirvReflectResult(-5);
    public static final SpirvReflectResult SPV_REFLECT_RESULT_ERROR_NULL_POINTER = new SpirvReflectResult(-4);
    public static final SpirvReflectResult SPV_REFLECT_RESULT_ERROR_PARSE_FAILED = new SpirvReflectResult(-1);
    public static final SpirvReflectResult SPV_REFLECT_RESULT_ERROR_RANGE_EXCEEDED = new SpirvReflectResult(-3);
    public static final SpirvReflectResult SPV_REFLECT_RESULT_ERROR_SPIRV_INVALID_BLOCK_MEMBER_REFERENCE = new SpirvReflectResult(-17);
    public static final SpirvReflectResult SPV_REFLECT_RESULT_ERROR_SPIRV_INVALID_CODE_SIZE = new SpirvReflectResult(-8);
    public static final SpirvReflectResult SPV_REFLECT_RESULT_ERROR_SPIRV_INVALID_ENTRY_POINT = new SpirvReflectResult(-18);
    public static final SpirvReflectResult SPV_REFLECT_RESULT_ERROR_SPIRV_INVALID_EXECUTION_MODE = new SpirvReflectResult(-19);
    public static final SpirvReflectResult SPV_REFLECT_RESULT_ERROR_SPIRV_INVALID_ID_REFERENCE = new SpirvReflectResult(-11);
    public static final SpirvReflectResult SPV_REFLECT_RESULT_ERROR_SPIRV_INVALID_INSTRUCTION = new SpirvReflectResult(-15);
    public static final SpirvReflectResult SPV_REFLECT_RESULT_ERROR_SPIRV_INVALID_MAGIC_NUMBER = new SpirvReflectResult(-9);
    public static final SpirvReflectResult SPV_REFLECT_RESULT_ERROR_SPIRV_INVALID_STORAGE_CLASS = new SpirvReflectResult(-13);
    public static final SpirvReflectResult SPV_REFLECT_RESULT_ERROR_SPIRV_RECURSION = new SpirvReflectResult(-14);
    public static final SpirvReflectResult SPV_REFLECT_RESULT_ERROR_SPIRV_SET_NUMBER_DUPLICATE = new SpirvReflectResult(-12);
    public static final SpirvReflectResult SPV_REFLECT_RESULT_ERROR_SPIRV_UNEXPECTED_BLOCK_DATA = new SpirvReflectResult(-16);
    public static final SpirvReflectResult SPV_REFLECT_RESULT_ERROR_SPIRV_UNEXPECTED_EOF = new SpirvReflectResult(-10);
    public static final SpirvReflectResult SPV_REFLECT_RESULT_NOT_READY = new SpirvReflectResult(1);
    public static final SpirvReflectResult SPV_REFLECT_RESULT_SUCCESS = new SpirvReflectResult(0);

    public static SpirvReflectResult fromValue(int value) {
        return switch (value) {
            case -2 -> SPV_REFLECT_RESULT_ERROR_ALLOC_FAILED;
            case -6 -> SPV_REFLECT_RESULT_ERROR_COUNT_MISMATCH;
            case -7 -> SPV_REFLECT_RESULT_ERROR_ELEMENT_NOT_FOUND;
            case -5 -> SPV_REFLECT_RESULT_ERROR_INTERNAL_ERROR;
            case -4 -> SPV_REFLECT_RESULT_ERROR_NULL_POINTER;
            case -1 -> SPV_REFLECT_RESULT_ERROR_PARSE_FAILED;
            case -3 -> SPV_REFLECT_RESULT_ERROR_RANGE_EXCEEDED;
            case -17 -> SPV_REFLECT_RESULT_ERROR_SPIRV_INVALID_BLOCK_MEMBER_REFERENCE;
            case -8 -> SPV_REFLECT_RESULT_ERROR_SPIRV_INVALID_CODE_SIZE;
            case -18 -> SPV_REFLECT_RESULT_ERROR_SPIRV_INVALID_ENTRY_POINT;
            case -19 -> SPV_REFLECT_RESULT_ERROR_SPIRV_INVALID_EXECUTION_MODE;
            case -11 -> SPV_REFLECT_RESULT_ERROR_SPIRV_INVALID_ID_REFERENCE;
            case -15 -> SPV_REFLECT_RESULT_ERROR_SPIRV_INVALID_INSTRUCTION;
            case -9 -> SPV_REFLECT_RESULT_ERROR_SPIRV_INVALID_MAGIC_NUMBER;
            case -13 -> SPV_REFLECT_RESULT_ERROR_SPIRV_INVALID_STORAGE_CLASS;
            case -14 -> SPV_REFLECT_RESULT_ERROR_SPIRV_RECURSION;
            case -12 -> SPV_REFLECT_RESULT_ERROR_SPIRV_SET_NUMBER_DUPLICATE;
            case -16 -> SPV_REFLECT_RESULT_ERROR_SPIRV_UNEXPECTED_BLOCK_DATA;
            case -10 -> SPV_REFLECT_RESULT_ERROR_SPIRV_UNEXPECTED_EOF;
            case 1 -> SPV_REFLECT_RESULT_NOT_READY;
            case 0 -> SPV_REFLECT_RESULT_SUCCESS;
            default -> new SpirvReflectResult(value);
        };
    }
}
