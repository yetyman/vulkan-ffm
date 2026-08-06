package io.github.yetyman.vulkan.buffers;

import java.lang.foreign.MemorySegment;

/**
 * Strategy interface for controlling the GPU memory layout of a type when writing to/reading from
 * native memory. Allows the same logical type to be serialized in different formats depending on
 * the consumer (e.g. column-major vs row-major matrices, packed vs std140-padded vectors,
 * DFS vs BFS vs Morton tree layouts, quantized vs full-precision attributes).
 *
 * <p>Layouts are offset-explicit and stateless with respect to their destination: they never
 * advance a cursor. This is deliberate and has two consequences that matter for bulk data:
 * <ul>
 *   <li>A large buffer can be filled in parallel across threads, since each worker writes at
 *       its own offsets with no shared position to coordinate.</li>
 *   <li>A single element deep inside a large array can be patched with one call, with no slicing.</li>
 * </ul>
 *
 * <p>Implementations should be stateless singletons (static final fields on the type they
 * serialize). When stored as a final field (e.g. on a {@code TypedVkBuffer}), the JIT can inline
 * through the dispatch and eliminate all indirection overhead.
 *
 * <p>Implementations should use unaligned value layouts
 * ({@link java.lang.foreign.ValueLayout#JAVA_FLOAT_UNALIGNED} and friends), because a packed
 * layout places elements at offsets with no guaranteed natural alignment.
 *
 * @param <T> the type this layout knows how to serialize
 */
public interface GpuLayout<T> {

    /**
     * @return the byte size of one element when written in this layout
     */
    int byteSize();

    /**
     * Writes {@code value} into {@code dst} starting at {@code offset}, in this layout.
     * Exactly {@link #byteSize()} bytes are written. The destination is not otherwise modified.
     */
    void writeTo(T value, MemorySegment dst, long offset);

    /**
     * Reads into {@code value} from {@code src} starting at {@code offset}, in this layout.
     * Exactly {@link #byteSize()} bytes are read.
     */
    void readFrom(T value, MemorySegment src, long offset);
}
