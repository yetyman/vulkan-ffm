package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.VkQueue;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

public interface ManagedBuffer extends AutoCloseable {
    
    void write(ByteBuffer data, long offset, VkQueue queue);
    TransferCompletion writeAsync(ByteBuffer data, long offset, VkQueue queue);
    
    /**
     * Reads data from buffer synchronously.
     * Note: DEVICE_LOCAL strategy will create staging buffer and stall pipeline - very slow.
     * Check memoryStrategy() and prefer async operations for device-local buffers.
     */
    ByteBuffer read(long offset, long size);
    
    void flush();

    void copyTo(ManagedBuffer dst, long srcOffset, long dstOffset, long length, VkQueue queue);
    TransferCompletion copyToAsync(ManagedBuffer dst, long srcOffset, long dstOffset, long length, VkQueue queue);
    
    // Vulkan binding (usage-specific)
    MemorySegment handle();
    
    // Resource management
    long size();
    BufferUsage usage();
    MemoryStrategy memoryStrategy();
    
    @Override
    void close();
}