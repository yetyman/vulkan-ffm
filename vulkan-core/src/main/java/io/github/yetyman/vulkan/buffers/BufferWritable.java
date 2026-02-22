package io.github.yetyman.vulkan.buffers;

import java.nio.ByteBuffer;

/**
 * Implemented by types that can be serialized to/from a {@link ByteBuffer} for GPU upload.
 * {@link #byteSize()} defines the fixed stride used by {@link TypedVkBuffer}.
 */
public interface BufferWritable {
    /** @return the fixed byte size of this type's GPU representation */
    int byteSize();

    /** Writes this object's GPU representation into {@code buf} at its current position. */
    void writeTo(ByteBuffer buf);

    /**
     * Reads this object's GPU representation from {@code buf} at its current position.
     * Implementations may throw {@link UnsupportedOperationException} if reading is not supported.
     */
    void readFrom(ByteBuffer buf);
}
