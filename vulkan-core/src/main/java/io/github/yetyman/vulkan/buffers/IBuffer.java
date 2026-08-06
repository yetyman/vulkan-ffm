package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.VkQueue;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

/**
 * Generic contract for anything that behaves like a GPU-visible byte buffer.
 * This is the minimal surface consumers (descriptor binding, typed views, render graph
 * adapters, custom user buffers) can rely on without depending on how the buffer is
 * allocated or how data actually moves between CPU and GPU.
 *
 * <p>{@link ManagedBuffer} is the primary implementation, composing an {@link AllocationStrategy}
 * and a {@link TransferStrategy}. Custom implementations (e.g. adapters over externally-owned
 * handles, graph-transient resources) may implement this interface directly.
 */
public interface IBuffer extends AutoCloseable {

    void write(ByteBuffer data, long offset, VkQueue queue);

    GpuCompletion writeAsync(ByteBuffer data, long offset, VkQueue queue);

    /**
     * Acquires writable memory for {@code [offset, offset + size)} that is as close to final as
     * this buffer's strategy allows, so a producer writes exactly once with no intermediate buffer
     * of its own. See {@link BufferWriteScope}.
     *
     * <p>This is the preferred write path for bulk data: primitive arrays, transcoded geometry,
     * memory-mapped file contents. {@link #write} and {@link #writeAsync} are conveniences for
     * callers that already hold a {@link ByteBuffer}.
     */
    BufferWriteScope acquireWrite(long offset, long size, VkQueue queue);

    /**
     * Acquires readable memory for {@code [offset, offset + size)}. Zero-copy for mapped and ReBAR
     * buffers; a pipeline-stalling readback for device-local ones.
     */
    BufferReadScope acquireRead(long offset, long size, VkQueue queue);

    /**
     * Reads data from buffer synchronously.
     * Note: strategies without a persistent CPU mapping will create a staging buffer and stall
     * the pipeline — this is slow. Prefer async operations or a CPU mirror where available.
     */
    ByteBuffer read(long offset, long size);

    void flush();

    void copyTo(IBuffer dst, long srcOffset, long dstOffset, long length, VkQueue queue);

    GpuCompletion copyToAsync(IBuffer dst, long srcOffset, long dstOffset, long length, VkQueue queue);

    // Vulkan binding (usage-specific)
    MemorySegment handle();

    // Resource management
    long size();

    BufferUsage usage();

    @Override
    void close();
}
