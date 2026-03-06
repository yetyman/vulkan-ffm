package io.github.yetyman.spirv.enums;

/**
 * Type-safe constants for SpvReflectTypeFlags (bitmask — use & to test)
 * Generated from jextract bindings
 */
public record SpirvReflectTypeFlags(int value) {

    public static final SpirvReflectTypeFlags SPV_REFLECT_TYPE_FLAG_UNDEFINED = new SpirvReflectTypeFlags(0);
    public static final SpirvReflectTypeFlags SPV_REFLECT_TYPE_FLAG_VOID      = new SpirvReflectTypeFlags(1);
    public static final SpirvReflectTypeFlags SPV_REFLECT_TYPE_FLAG_BOOL      = new SpirvReflectTypeFlags(2);
    public static final SpirvReflectTypeFlags SPV_REFLECT_TYPE_FLAG_INT       = new SpirvReflectTypeFlags(4);
    public static final SpirvReflectTypeFlags SPV_REFLECT_TYPE_FLAG_FLOAT     = new SpirvReflectTypeFlags(8);
    public static final SpirvReflectTypeFlags SPV_REFLECT_TYPE_FLAG_VECTOR    = new SpirvReflectTypeFlags(256);
    public static final SpirvReflectTypeFlags SPV_REFLECT_TYPE_FLAG_MATRIX    = new SpirvReflectTypeFlags(512);
    public static final SpirvReflectTypeFlags SPV_REFLECT_TYPE_FLAG_STRUCT    = new SpirvReflectTypeFlags(2048);
    public static final SpirvReflectTypeFlags SPV_REFLECT_TYPE_FLAG_ARRAY     = new SpirvReflectTypeFlags(65536);

    public boolean isSet(int flags) { return (flags & value) != 0; }
}
