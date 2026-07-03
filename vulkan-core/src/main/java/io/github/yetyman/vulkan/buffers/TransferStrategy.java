package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.VkQueue;

import java.nio.ByteBuffer;

/**
 * Responsible for how data actually moves between CPU and GPU for a buffer.
 * Orthogonal to {@link AllocationStrategy}, which decides where the memory lives.
 *
 * <p>Implementations are stateless with respect to any single {@link ManagedBuffer} —
 * all buffer-specific state is passed in via {@link TransferContext} on every call.
 */
public interface TransferStrategy {

    TransferCompletion writeAsync(TransferContext ctx, ByteBuffer data, long offset, VkQueue queue);

    ByteBuffer read(TransferContext ctx, long offset, long length);

    void flush(TransferContext ctx);

    /**
     * Releases any native resources owned directly by this strategy (e.g. a persistent
     * staging buffer) that are not owned by the {@link ManagedBuffer}'s own arena/VkBuffer.
     * Called once by {@link ManagedBuffer#close()}. No-op by default.
     */
    default void close(TransferContext ctx) {
    }
}
