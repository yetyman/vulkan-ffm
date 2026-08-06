package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.VkQueue;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

/**
 * Responsible for how data actually moves between CPU and GPU for a buffer.
 * Orthogonal to {@link AllocationStrategy}, which decides where the memory lives.
 *
 * <p>Implementations are stateless with respect to any single {@link ManagedBuffer} —
 * all buffer-specific state is passed in via {@link TransferContext} on every call.
 */
public interface TransferStrategy {

    GpuCompletion writeAsync(TransferContext ctx, ByteBuffer data, long offset, VkQueue queue);

    /**
     * Hands back writable memory for {@code [offset, offset + size)} that is as close to final as
     * this strategy allows, so the caller writes exactly once. See {@link BufferWriteScope}.
     */
    BufferWriteScope acquireWrite(TransferContext ctx, long offset, long size, VkQueue queue);

    ByteBuffer read(TransferContext ctx, long offset, long length);

    /**
     * Hands back readable memory for {@code [offset, offset + size)}.
     *
     * <p>The default implementation routes through {@link #read}, which for staged strategies means
     * a pipeline-stalling readback into temporary host memory. Strategies with persistently mapped
     * memory override this to return the mapped region directly, with no copy.
     */
    default BufferReadScope acquireRead(TransferContext ctx, long offset, long size, VkQueue queue) {
        ByteBuffer data = read(ctx, offset, size);
        return BufferReadScope.of(MemorySegment.ofBuffer(data), offset, size, null);
    }

    void flush(TransferContext ctx);

    /**
     * Releases any native resources owned directly by this strategy (e.g. a persistent
     * staging buffer) that are not owned by the {@link ManagedBuffer}'s own arena/VkBuffer.
     * Called once by {@link ManagedBuffer#close()}. No-op by default.
     */
    default void close(TransferContext ctx) {
    }
}
