package io.github.yetyman.shaderc.enums;

import java.util.*;

/**
 * Type-safe constants for ShadercSpirvVersion
 * Generated from jextract bindings
 */
public record ShadercSpirvVersion(int value) {

    public static final ShadercSpirvVersion shaderc_spirv_version_1_0 = new ShadercSpirvVersion(65536);
    public static final ShadercSpirvVersion shaderc_spirv_version_1_1 = new ShadercSpirvVersion(65792);
    public static final ShadercSpirvVersion shaderc_spirv_version_1_2 = new ShadercSpirvVersion(66048);
    public static final ShadercSpirvVersion shaderc_spirv_version_1_3 = new ShadercSpirvVersion(66304);
    public static final ShadercSpirvVersion shaderc_spirv_version_1_4 = new ShadercSpirvVersion(66560);
    public static final ShadercSpirvVersion shaderc_spirv_version_1_5 = new ShadercSpirvVersion(66816);
    public static final ShadercSpirvVersion shaderc_spirv_version_1_6 = new ShadercSpirvVersion(67072);

    public static ShadercSpirvVersion fromValue(int value) {
        return switch (value) {
            case 65536 -> shaderc_spirv_version_1_0;
            case 65792 -> shaderc_spirv_version_1_1;
            case 66048 -> shaderc_spirv_version_1_2;
            case 66304 -> shaderc_spirv_version_1_3;
            case 66560 -> shaderc_spirv_version_1_4;
            case 66816 -> shaderc_spirv_version_1_5;
            case 67072 -> shaderc_spirv_version_1_6;
            default -> new ShadercSpirvVersion(value);
        };
    }
}
