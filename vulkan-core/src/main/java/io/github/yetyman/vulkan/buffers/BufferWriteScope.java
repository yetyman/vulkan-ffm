package io.github.yetyman.vulkan.buffers;

import java.lang.foreign.MemorySegment;
import java.util.function.Supplier;

/**
 * A borrowed, writable region of native memory that will end up in a buffer range.
 *
 * <p>The point of this type is that the producer of bytes should not own the intermediate memory.
 * Which memory is "closest to final" depends entirely on the buffer's {@link TransferStrategy},
 * which is the buffer's business, not the producer's. Acquiring a scope lets a producer write
 * exactly once, into memory that is already as close to final as the strategy allows:
 *
 * <ul>
 *   <li>mapped and ReBAR buffers hand back the mapped device memory itself, so the write lands
 *       directly with no copy and no GPU command at all</li>
 *   <li>staged buffers hand back mapped staging memory, and {@link #close()} records the
 *       {@code vkCmdCopyBuffer}</li>
 *   <li>non-coherent memory is flushed on {@link #close()}</li>
 * </ul>
 *
 * <p>Typical use:
 * <pre>{@code
 * try (var scope = buffer.acquireWrite(offset, byteCount, queue)) {
 *     MemorySegment.copy(src, srcOffset, scope.segment(), JAVA_FLOAT_UNALIGNED, 0, count);
 * }
 * }</pre>
 *
 * <p>The segment is valid only until {@link #close()}. Do not retain it.
 */
public interface BufferWriteScope extends AutoCloseable {

    /**
     * @return the memory to write into. Its base corresponds to {@link #offset()} in the buffer,
     * so writes are made at segment-relative offsets starting from zero, not at buffer offsets.
     */
    MemorySegment segment();

    /**
     * @return the destination offset within the buffer this scope writes to
     */
    long offset();

    /**
     * @return the byte length of this scope
     */
    long size();

    /**
     * @return the completion for any GPU work this scope submitted. Meaningful only after
     * {@link #close()}; before that it reports already-complete. Never null.
     */
    GpuCompletion completion();

    /**
     * Commits the write: flushes non-coherent memory and/or records the copy into the destination.
     * Idempotent.
     */
    @Override
    void close();

    /**
     * Creates a scope over {@code segment} whose {@link #close()} runs {@code onCommit} and adopts
     * its completion.
     *
     * @param onCommit action performed on close, returning the completion for any GPU work it
     *                 submitted. May be null when nothing needs to happen on close, which is the
     *                 case for coherent persistently-mapped memory.
     */
    static BufferWriteScope of(MemorySegment segment, long offset, long size, Supplier<GpuCompletion> onCommit) {
        return new DefaultBufferWriteScope(segment, offset, size, onCommit);
    }
}
