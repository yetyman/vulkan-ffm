package io.github.yetyman.vulkan.buffers;

import java.lang.foreign.MemorySegment;

/**
 * A borrowed, readable region of native memory reflecting a buffer range.
 *
 * <p>The counterpart to {@link BufferWriteScope}. Mapped and ReBAR buffers hand back the mapped
 * device memory itself, so reads are zero-copy. Device-local buffers perform a readback into
 * temporary host memory, which stalls the pipeline; prefer a mirrored buffer
 * ({@code BufferFactory.create(DEVICE_LOCAL_MIRRORED, ...)}) for frequent reads.
 *
 * <p>The segment is valid only until {@link #close()}. Do not retain it.
 */
public interface BufferReadScope extends AutoCloseable {

    /**
     * @return the memory to read from, based at {@link #offset()} in the buffer
     */
    MemorySegment segment();

    /**
     * @return the source offset within the buffer this scope reads from
     */
    long offset();

    /**
     * @return the byte length of this scope
     */
    long size();

    /**
     * Releases any temporary resources the readback needed. Idempotent.
     */
    @Override
    void close();

    /**
     * Creates a scope over {@code segment} whose {@link #close()} runs {@code onClose}.
     *
     * @param onClose action performed on close, or null when nothing needs releasing
     */
    static BufferReadScope of(MemorySegment segment, long offset, long size, Runnable onClose) {
        return new DefaultBufferReadScope(segment, offset, size, onClose);
    }
}
