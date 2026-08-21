package io.github.yetyman.vulkan.shaders;

import io.github.yetyman.spirv.generated.SpirvReflectFFM;

/**
 * Describes the access qualifier of a shader descriptor binding, as declared in SPIR-V.
 *
 * <p>Derived from SPIRV-Reflect's decoration flags:
 * <ul>
 *   <li>{@code NonWritable} decoration -> {@link #READ_ONLY}</li>
 *   <li>{@code NonReadable} decoration -> {@link #WRITE_ONLY}</li>
 *   <li>Neither decoration -> {@link #READ_WRITE}</li>
 * </ul>
 *
 * <p>These are conservative bounds: a buffer declared {@code READ_WRITE} in SPIR-V may only
 * be written in some shader code paths. The render graph accepts manual {@code ResourceEdge}
 * overrides where the user knows better than the static declaration.
 */
public enum AccessQualifier {

    /** The shader only reads from this binding (GLSL: {@code readonly}). */
    READ_ONLY,

    /** The shader only writes to this binding (GLSL: {@code writeonly}). */
    WRITE_ONLY,

    /** The shader may both read and write this binding (no qualifier or {@code coherent}). */
    READ_WRITE;

    /**
     * Derives the access qualifier from SPIRV-Reflect decoration flags.
     *
     * @param decorationFlags the {@code SpvReflectDecorationFlags} bitmask from the binding
     * @return the inferred access qualifier
     */
    public static AccessQualifier fromDecorationFlags(int decorationFlags) {
        boolean nonWritable = (decorationFlags & SpirvReflectFFM.SPV_REFLECT_DECORATION_NON_WRITABLE()) != 0;
        boolean nonReadable = (decorationFlags & SpirvReflectFFM.SPV_REFLECT_DECORATION_NON_READABLE()) != 0;

        if (nonWritable && !nonReadable) return READ_ONLY;
        if (nonReadable && !nonWritable) return WRITE_ONLY;
        // Both or neither: default to READ_WRITE
        return READ_WRITE;
    }
}
