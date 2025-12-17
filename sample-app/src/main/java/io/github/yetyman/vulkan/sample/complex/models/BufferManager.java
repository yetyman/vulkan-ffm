package io.github.yetyman.vulkan.sample.complex.models;

import io.github.yetyman.vulkan.VkBuffer;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages multiple buffer pools of different sizes for optimal allocation
 */
public class BufferManager {
    private final Map<Long, BufferPool> vertexPools = new HashMap<>();
    private final Map<Long, BufferPool> indexPools = new HashMap<>();
    private final Arena arena;
    private final MemorySegment device;
    private final MemorySegment physicalDevice;
    
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
        this.arena = arena;
        this.device = device;
        this.physicalDevice = physicalDevice;
        
        // Create pools for each size
        for (long size : BUFFER_SIZES) {
            vertexPools.put(size, new BufferPool(arena, device, physicalDevice, size, true, 32));
            indexPools.put(size, new BufferPool(arena, device, physicalDevice, size, false, 32));
        }
        
        // Pre-allocate some common sizes including small test buffers
        vertexPools.get(1024L).preallocate(20);       // More small test buffers
        vertexPools.get(4 * 1024L).preallocate(20);   // More small test buffers
        vertexPools.get(256 * 1024L).preallocate(8);
        vertexPools.get(1024 * 1024L).preallocate(4);
        indexPools.get(1024L).preallocate(4);         // Small test buffers
        indexPools.get(64 * 1024L).preallocate(8);
        indexPools.get(256 * 1024L).preallocate(4);
    }
    
    /**
     * Acquire vertex buffer that can fit the requested size
     */
    public VkBuffer acquireVertexBuffer(long requiredSize) {
        return acquireBuffer(vertexPools, requiredSize);
    }
    
    /**
     * Acquire index buffer that can fit the requested size
     */
    public VkBuffer acquireIndexBuffer(long requiredSize) {
        return acquireBuffer(indexPools, requiredSize);
    }
    
    private VkBuffer acquireBuffer(Map<Long, BufferPool> pools, long requiredSize) {
        // Find smallest buffer size that fits
        for (long size : BUFFER_SIZES) {
            if (size >= requiredSize) {
                BufferPool pool = pools.get(size);
                VkBuffer buffer = pool.acquireBuffer();
                if (buffer != null) {
                    return buffer;
                }
            }
        }
        
        // No suitable buffer available
        System.err.println("[BUFFER] No buffer available for size: " + requiredSize);
        return null;
    }
    
    public void releaseVertexBuffer(VkBuffer buffer) {
        releaseBuffer(vertexPools, buffer);
    }
    
    public void releaseIndexBuffer(VkBuffer buffer) {
        releaseBuffer(indexPools, buffer);
    }
    
    private void releaseBuffer(Map<Long, BufferPool> pools, VkBuffer buffer) {
        if (buffer == null) return;
        
        // Find the pool this buffer belongs to
        long bufferSize = buffer.size();
        BufferPool pool = pools.get(bufferSize);
        if (pool != null) {
            pool.releaseBuffer(buffer);
        }
    }
    
    public void printStats() {
        System.out.println("[BUFFER] Pool Statistics:");
        for (long size : BUFFER_SIZES) {
            BufferPool vPool = vertexPools.get(size);
            BufferPool iPool = indexPools.get(size);
            System.out.println("  " + (size / 1024) + "KB: V=" + vPool.getTotalUsed() + "/" + vPool.getTotalAllocated() + 
                             ", I=" + iPool.getTotalUsed() + "/" + iPool.getTotalAllocated());
        }
    }
    
    public void cleanup() {
        for (BufferPool pool : vertexPools.values()) {
            pool.cleanup();
        }
        for (BufferPool pool : indexPools.values()) {
            pool.cleanup();
        }
    }
}