package io.github.yetyman.shaderc.enums;

import java.util.*;

/**
 * Type-safe constants for ShadercEnvVersion
 * Generated from jextract bindings
 */
public record ShadercEnvVersion(int value) {

    public static final ShadercEnvVersion shaderc_env_version_opengl_4_5 = new ShadercEnvVersion(450);
    public static final ShadercEnvVersion shaderc_env_version_vulkan_1_0 = new ShadercEnvVersion(4194304);
    public static final ShadercEnvVersion shaderc_env_version_vulkan_1_1 = new ShadercEnvVersion(4198400);
    public static final ShadercEnvVersion shaderc_env_version_vulkan_1_2 = new ShadercEnvVersion(4202496);
    public static final ShadercEnvVersion shaderc_env_version_vulkan_1_3 = new ShadercEnvVersion(4206592);
    public static final ShadercEnvVersion shaderc_env_version_webgpu = shaderc_env_version_vulkan_1_0;

    public static ShadercEnvVersion fromValue(int value) {
        return switch (value) {
            case 450 -> shaderc_env_version_opengl_4_5;
            case 4194304 -> shaderc_env_version_webgpu;
            case 4198400 -> shaderc_env_version_vulkan_1_1;
            case 4202496 -> shaderc_env_version_vulkan_1_2;
            case 4206592 -> shaderc_env_version_vulkan_1_3;
            default -> new ShadercEnvVersion(value);
        };
    }
}
