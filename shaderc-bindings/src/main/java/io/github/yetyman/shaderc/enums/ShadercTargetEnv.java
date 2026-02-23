package io.github.yetyman.shaderc.enums;

import java.util.*;

/**
 * Type-safe constants for ShadercTargetEnv
 * Generated from jextract bindings
 */
public record ShadercTargetEnv(int value) {

    public static final ShadercTargetEnv shaderc_target_env_opengl = new ShadercTargetEnv(1);
    public static final ShadercTargetEnv shaderc_target_env_opengl_compat = new ShadercTargetEnv(2);
    public static final ShadercTargetEnv shaderc_target_env_vulkan = new ShadercTargetEnv(0);
    public static final ShadercTargetEnv shaderc_target_env_webgpu = new ShadercTargetEnv(3);

    public static ShadercTargetEnv fromValue(int value) {
        return switch (value) {
            case 1 -> shaderc_target_env_opengl;
            case 2 -> shaderc_target_env_opengl_compat;
            case 0 -> shaderc_target_env_vulkan;
            case 3 -> shaderc_target_env_webgpu;
            default -> new ShadercTargetEnv(value);
        };
    }
}
