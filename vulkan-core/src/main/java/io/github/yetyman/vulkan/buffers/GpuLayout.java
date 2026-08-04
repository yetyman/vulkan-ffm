package io.github.yetyman.vulkan.buffers;

import java.nio.ByteBuffer;

/**
 * Strategy interface for controlling the GPU memory layout of a type when writing to/reading from buffers.
 * Allows the same logical type to be serialized in different formats depending on the consumer
 * (e.g., column-major vs row-major matrices, padded vs packed vectors, DFS vs BFS tree layouts).
 *
 * Implementations should be stateless singletons (static final fields on the type they serialize).
 * When stored as a final field (e.g., on a TypedVkBuffer), the JIT can inline through the
 * dispatch and eliminate all indirection overhead.
 *
 * @param <T> the type this layout knows how to serialize
 */
public interface GpuLayout<T> {

    /**
     * @return the byte size of one element when written in this layout
     */
    int byteSize();

    /**
     * Writes the value into the buffer at its current position using this layout.
     */
    void writeTo(T value, ByteBuffer buf);

    /**
     * Reads from the buffer at its current position into the value using this layout.
     */
    void readFrom(T value, ByteBuffer buf);
}
