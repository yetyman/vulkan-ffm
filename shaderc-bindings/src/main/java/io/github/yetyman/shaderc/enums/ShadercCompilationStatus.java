package io.github.yetyman.shaderc.enums;

import java.util.*;

/**
 * Type-safe constants for ShadercCompilationStatus
 * Generated from jextract bindings
 */
public record ShadercCompilationStatus(int value) {

    public static final ShadercCompilationStatus shaderc_compilation_status_compilation_error = new ShadercCompilationStatus(2);
    public static final ShadercCompilationStatus shaderc_compilation_status_configuration_error = new ShadercCompilationStatus(8);
    public static final ShadercCompilationStatus shaderc_compilation_status_internal_error = new ShadercCompilationStatus(3);
    public static final ShadercCompilationStatus shaderc_compilation_status_invalid_assembly = new ShadercCompilationStatus(5);
    public static final ShadercCompilationStatus shaderc_compilation_status_invalid_stage = new ShadercCompilationStatus(1);
    public static final ShadercCompilationStatus shaderc_compilation_status_null_result_object = new ShadercCompilationStatus(4);
    public static final ShadercCompilationStatus shaderc_compilation_status_success = new ShadercCompilationStatus(0);
    public static final ShadercCompilationStatus shaderc_compilation_status_transformation_error = new ShadercCompilationStatus(7);
    public static final ShadercCompilationStatus shaderc_compilation_status_validation_error = new ShadercCompilationStatus(6);

    public static ShadercCompilationStatus fromValue(int value) {
        return switch (value) {
            case 2 -> shaderc_compilation_status_compilation_error;
            case 8 -> shaderc_compilation_status_configuration_error;
            case 3 -> shaderc_compilation_status_internal_error;
            case 5 -> shaderc_compilation_status_invalid_assembly;
            case 1 -> shaderc_compilation_status_invalid_stage;
            case 4 -> shaderc_compilation_status_null_result_object;
            case 0 -> shaderc_compilation_status_success;
            case 7 -> shaderc_compilation_status_transformation_error;
            case 6 -> shaderc_compilation_status_validation_error;
            default -> new ShadercCompilationStatus(value);
        };
    }
}
