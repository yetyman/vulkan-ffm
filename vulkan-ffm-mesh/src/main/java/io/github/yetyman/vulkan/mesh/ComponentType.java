package io.github.yetyman.vulkan.mesh;

/**
 * Scalar component encoding of one channel of an attribute.
 *
 * <p>{@link #PACKED} covers every encoding whose channels are not independent scalars:
 * octahedral normals, bitfield-packed identifiers, 10-10-10-2 vectors, and anything a shader
 * decodes by hand. A packed format's size comes from the {@link AttributeFormat}, not from here.
 */
public enum ComponentType {
    /** 32-bit float. */
    F32(4, false, true),
    /** 16-bit half float. */
    F16(2, false, true),
    /** 64-bit double. */
    F64(8, false, true),
    /** Unsigned 8-bit integer. */
    U8(1, true, false),
    /** Signed 8-bit integer. */
    S8(1, true, false),
    /** Unsigned 16-bit integer. */
    U16(2, true, false),
    /** Signed 16-bit integer. */
    S16(2, true, false),
    /** Unsigned 32-bit integer. */
    U32(4, true, false),
    /** Signed 32-bit integer. */
    S32(4, true, false),
    /** Unsigned 64-bit integer. */
    U64(8, true, false),
    /** Signed 64-bit integer. */
    S64(8, true, false),
    /** Opaque packed encoding; size is carried by the {@link AttributeFormat}. */
    PACKED(0, false, false);

    private final int byteSize;
    private final boolean integer;
    private final boolean floatingPoint;

    ComponentType(int byteSize, boolean integer, boolean floatingPoint) {
        this.byteSize = byteSize;
        this.integer = integer;
        this.floatingPoint = floatingPoint;
    }

    /**
     * @return bytes per component, or 0 for {@link #PACKED}
     */
    public int byteSize() {
        return byteSize;
    }

    /**
     * @return true for integer component types
     */
    public boolean isInteger() {
        return integer;
    }

    /**
     * @return true for floating-point component types
     */
    public boolean isFloatingPoint() {
        return floatingPoint;
    }

    /**
     * @return true if this component type can be interpreted as a normalized fixed-point value
     */
    public boolean supportsNormalized() {
        return this == U8 || this == S8 || this == U16 || this == S16;
    }
}
