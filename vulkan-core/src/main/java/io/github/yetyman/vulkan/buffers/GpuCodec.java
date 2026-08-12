package io.github.yetyman.vulkan.buffers;

import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.JAVA_DOUBLE_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_LONG_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_SHORT_UNALIGNED;

/**
 * A {@link GpuLayout} that can also decode a value from a memory segment, returning a new instance
 * rather than mutating an existing one. This is the correct contract for immutable types (boxed
 * primitives, records, any value object) where the mutable {@link GpuLayout#readFrom} cannot work.
 *
 * <p>GpuCodec extends GpuLayout so it can be used anywhere a GpuLayout is accepted (MetadataChannel,
 * TypedVkBuffer, etc.) while additionally supporting readback into new values.
 *
 * <p>For mutable types (Vec3, Mat4, AABB), the standard {@link GpuLayout} with its in-place
 * {@code readFrom} remains the preferred contract since it avoids allocation on every read. GpuCodec
 * is for types where allocation per read is unavoidable because the type is immutable.
 *
 * <p>Well-known codecs for Java primitives are provided as static fields. Custom codecs for
 * records or other immutable value types follow the same pattern.
 *
 * @param <T> the type this codec knows how to encode and decode
 */
public interface GpuCodec<T> extends GpuLayout<T> {

    /**
     * Decodes a value from {@code src} starting at {@code offset}. Returns a new instance.
     * Exactly {@link #byteSize()} bytes are read.
     *
     * @param src    the source segment to read from
     * @param offset byte offset into src
     * @return the decoded value (never null for primitive codecs)
     */
    T decode(MemorySegment src, long offset);

    /**
     * Default implementation of the mutable readFrom: delegates to decode and discards the result.
     * This satisfies the GpuLayout contract but is inherently a no-op for immutable types.
     * Callers holding a GpuCodec should prefer {@link #decode} over readFrom.
     */
    @Override
    default void readFrom(T value, MemorySegment src, long offset) {
        // Cannot mutate an immutable value. Callers should use decode() instead.
        // This default exists solely to satisfy the GpuLayout interface contract.
    }

    // -------------------------------------------------------------------------
    // Well-known primitive codecs
    // -------------------------------------------------------------------------

    /** Float codec: 4 bytes, native byte order, unaligned. */
    GpuCodec<Float> FLOAT = new GpuCodec<>() {
        @Override public int byteSize() { return 4; }
        @Override public void writeTo(Float value, MemorySegment dst, long offset) {
            dst.set(JAVA_FLOAT_UNALIGNED, offset, value);
        }
        @Override public Float decode(MemorySegment src, long offset) {
            return src.get(JAVA_FLOAT_UNALIGNED, offset);
        }
    };

    /** Integer codec: 4 bytes (uint32/int32), native byte order, unaligned. */
    GpuCodec<Integer> INT = new GpuCodec<>() {
        @Override public int byteSize() { return 4; }
        @Override public void writeTo(Integer value, MemorySegment dst, long offset) {
            dst.set(JAVA_INT_UNALIGNED, offset, value);
        }
        @Override public Integer decode(MemorySegment src, long offset) {
            return src.get(JAVA_INT_UNALIGNED, offset);
        }
    };

    /** Long codec: 8 bytes (uint64/int64), native byte order, unaligned. */
    GpuCodec<Long> LONG = new GpuCodec<>() {
        @Override public int byteSize() { return 8; }
        @Override public void writeTo(Long value, MemorySegment dst, long offset) {
            dst.set(JAVA_LONG_UNALIGNED, offset, value);
        }
        @Override public Long decode(MemorySegment src, long offset) {
            return src.get(JAVA_LONG_UNALIGNED, offset);
        }
    };

    /** Short codec: 2 bytes (uint16/int16), native byte order, unaligned. */
    GpuCodec<Short> SHORT = new GpuCodec<>() {
        @Override public int byteSize() { return 2; }
        @Override public void writeTo(Short value, MemorySegment dst, long offset) {
            dst.set(JAVA_SHORT_UNALIGNED, offset, value);
        }
        @Override public Short decode(MemorySegment src, long offset) {
            return src.get(JAVA_SHORT_UNALIGNED, offset);
        }
    };

    /** Double codec: 8 bytes, native byte order, unaligned. */
    GpuCodec<Double> DOUBLE = new GpuCodec<>() {
        @Override public int byteSize() { return 8; }
        @Override public void writeTo(Double value, MemorySegment dst, long offset) {
            dst.set(JAVA_DOUBLE_UNALIGNED, offset, value);
        }
        @Override public Double decode(MemorySegment src, long offset) {
            return src.get(JAVA_DOUBLE_UNALIGNED, offset);
        }
    };
}
