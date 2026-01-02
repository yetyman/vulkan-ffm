package io.github.yetyman.vulkan.sample.complex.models;

import io.github.yetyman.vulkan.VkBuffer;
import io.github.yetyman.vulkan.highlevel.VkSizedResourcePool;
import io.github.yetyman.vulkan.util.Logger;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Map;

/**
 * Manages vertex and index buffer pools using VkSizedResourcePool
 */
public class BufferManager implements AutoCloseable {
    private final VkSizedResourcePool<VkBuffer> vertexPool;
    private final VkSizedResourcePool<VkBuffer> indexPool;
    private final MemorySegment device;
    
    // Standard buffer sizes (powers of 2 for good coverage)
    private static final long[] BUFFER_SIZES = {
        1024,         // 1KB - for small test buffers
        4 * 1024,     // 4KB
        64 * 1024,    // 64KB
        256 * 1024,   // 256KB  
        1024 * 1024,  // 1MB
        4 * 1024 * 1024,  // 4MB
        16 * 1024 * 1024  // 16MB
    };
    
    public BufferManager(Arena arena, MemorySegment device, MemorySegment physicalDevice) {
        this.device = device;
        
        // Create vertex buffer pool
        vertexPool = VkSizedResourcePool.builder(VkBuffer.class)
            .sizes(BUFFER_SIZES)
            .factory(size -> VkBuffer.builder()
                .device(device)
                .physicalDevice(physicalDevice)
                .size(size)
                .vertexBuffer()
                .transferDst()
                .deviceLocal()
                .build(Arena.ofShared()))
            .sizeExtractor(VkBuffer::size)
            .destroyFunction(VkBuffer::close)
            .maxPoolSizePerSize(32)
            .build();
        
        // Create index buffer pool
        indexPool = VkSizedResourcePool.builder(VkBuffer.class)
            .sizes(BUFFER_SIZES)
            .factory(size -> VkBuffer.builder()
                .device(device)
                .physicalDevice(physicalDevice)
                .size(size)
                .indexBuffer()
                .transferDst()
                .deviceLocal()
                .build(Arena.ofShared()))
            .sizeExtractor(VkBuffer::size)
            .destroyFunction(VkBuffer::close)
            .maxPoolSizePerSize(32)
            .build();
        
        // Pre-allocate common sizes
        vertexPool.preallocate(1024L, 20);
        vertexPool.preallocate(4 * 1024L, 20);
        vertexPool.preallocate(256 * 1024L, 8);
        vertexPool.preallocate(1024 * 1024L, 4);
        indexPool.preallocate(1024L, 4);
        indexPool.preallocate(64 * 1024L, 8);
        indexPool.preallocate(256 * 1024L, 4);
    }
    
    /**
     * Acquire vertex buffer that can fit the requested size
     */
    public VkBuffer acquireVertexBuffer(long requiredSize) {
        return vertexPool.acquire(requiredSize);
    }
    
    /**
     * Acquire index buffer that can fit the requested size
     */
    public VkBuffer acquireIndexBuffer(long requiredSize) {
        return indexPool.acquire(requiredSize);
    }
    
    public void releaseVertexBuffer(VkBuffer buffer) {
        vertexPool.release(buffer);
    }
    
    public void releaseIndexBuffer(VkBuffer buffer) {
        indexPool.release(buffer);
    }
    
    public void printStats() {
        Logger.debug("Vertex Pool Statistics:");
        Map<Long, VkSizedResourcePool.PoolStats> vStats = vertexPool.getStats();
        for (Map.Entry<Long, VkSizedResourcePool.PoolStats> entry : vStats.entrySet()) {
            Logger.debug("  " + (entry.getKey() / 1024) + "KB: " + entry.getValue().available + "/" + entry.getValue().total);
        }
        
        Logger.debug("Index Pool Statistics:");
        Map<Long, VkSizedResourcePool.PoolStats> iStats = indexPool.getStats();
        for (Map.Entry<Long, VkSizedResourcePool.PoolStats> entry : iStats.entrySet()) {
            Logger.debug("  " + (entry.getKey() / 1024) + "KB: " + entry.getValue().available + "/" + entry.getValue().total);
        }
    }
    
    public void cleanup() {
        close();
    }
    
    @Override
    public void close() {
        // Wait for device to be idle before cleanup
        if (device != null && !device.equals(MemorySegment.NULL)) {
            io.github.yetyman.vulkan.Vulkan.deviceWaitIdle(device).check();
            Logger.debug("Device idle - starting BufferManager cleanup");
        }
        
        vertexPool.close();
        indexPool.close();
        
        Logger.debug("BufferManager cleanup complete");
    }
}