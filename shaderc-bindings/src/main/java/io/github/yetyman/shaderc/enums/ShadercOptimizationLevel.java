package io.github.yetyman.shaderc.enums;

import java.util.*;

/**
 * Type-safe constants for ShadercOptimizationLevel
 * Generated from jextract bindings
 */
public record ShadercOptimizationLevel(int value) {

    public static final ShadercOptimizationLevel shaderc_optimization_level_performance = new ShadercOptimizationLevel(2);
    public static final ShadercOptimizationLevel shaderc_optimization_level_size = new ShadercOptimizationLevel(1);
    public static final ShadercOptimizationLevel shaderc_optimization_level_zero = new ShadercOptimizationLevel(0);

    public static ShadercOptimizationLevel fromValue(int value) {
        return switch (value) {
            case 2 -> shaderc_optimization_level_performance;
            case 1 -> shaderc_optimization_level_size;
            case 0 -> shaderc_optimization_level_zero;
            default -> new ShadercOptimizationLevel(value);
        };
    }
}
