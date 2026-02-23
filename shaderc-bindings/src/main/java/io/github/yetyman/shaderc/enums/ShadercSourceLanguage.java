package io.github.yetyman.shaderc.enums;

import java.util.*;

/**
 * Type-safe constants for ShadercSourceLanguage
 * Generated from jextract bindings
 */
public record ShadercSourceLanguage(int value) {

    public static final ShadercSourceLanguage shaderc_source_language_glsl = new ShadercSourceLanguage(0);
    public static final ShadercSourceLanguage shaderc_source_language_hlsl = new ShadercSourceLanguage(1);

    public static ShadercSourceLanguage fromValue(int value) {
        return switch (value) {
            case 0 -> shaderc_source_language_glsl;
            case 1 -> shaderc_source_language_hlsl;
            default -> new ShadercSourceLanguage(value);
        };
    }
}
