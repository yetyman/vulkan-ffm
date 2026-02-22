package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.VkCommandPool;
import io.github.yetyman.vulkan.VkQueue;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

public interface ManagedBuffer extends AutoCloseable {
    
    // Data access (strategy-agnostic)
    void write(ByteBuffer data, long offset);
    TransferCompletion writeAsync(ByteBuffer data, long offset);
    
    /**
     * Reads data from buffer synchronously.
     * Note: DEVICE_LOCAL strategy will create staging buffer and stall pipeline - very slow.
     * Check memoryStrategy() and prefer async operations for device-local buffers.
     */
    ByteBuffer read(long offset, long size);
    
    void flush(); // No-op for coherent memory and device-local buffers

    /**
     * Copies a region of this buffer into {@code dst} entirely on the GPU.
     * Both buffers must have been created with TRANSFER_SRC / TRANSFER_DST usage respectively.
     */
    void copyTo(ManagedBuffer dst, long srcOffset, long dstOffset, long length, VkQueue queue, VkCommandPool commandPool);

    /** @return a {@link TransferCompletion} that resolves when the GPU copy finishes. */
    TransferCompletion copyToAsync(ManagedBuffer dst, long srcOffset, long dstOffset, long length, VkQueue queue, VkCommandPool commandPool);
    
    // Vulkan binding (usage-specific)
    MemorySegment handle();
    
    // Resource management
    long size();
    BufferUsage usage();
    MemoryStrategy memoryStrategy();
    
    @Override
    void close();
}