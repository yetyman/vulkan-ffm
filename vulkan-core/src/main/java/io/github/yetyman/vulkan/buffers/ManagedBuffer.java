package io.github.yetyman.vulkan.buffers;

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
    
    // Vulkan binding (usage-specific)
    MemorySegment handle();
    
    // Resource management
    long size();
    BufferUsage usage();
    MemoryStrategy memoryStrategy();
    
    @Override
    void close();
}