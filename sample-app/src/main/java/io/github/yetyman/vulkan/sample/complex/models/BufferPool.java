package io.github.yetyman.vulkan.sample.complex.models;

import io.github.yetyman.vulkan.VkBuffer;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Pool of device-local GPU buffers for efficient memory management
 */
public class BufferPool {
    private final ConcurrentLinkedQueue<VkBuffer> availableBuffers = new ConcurrentLinkedQueue<>();
    private final AtomicLong totalAllocated = new AtomicLong(0);
    private final AtomicLong totalUsed = new AtomicLong(0);
    
    private final Arena arena;
    private final MemorySegment device;
    private final MemorySegment physicalDevice;
    private final long bufferSize;
    private final boolean isVertexBuffer;
    private final long maxPoolSize;
    private final Set<VkBuffer> globalBufferRegistry; // Reference to global registry
    
    public BufferPool(Arena arena, MemorySegment device, MemorySegment physicalDevice, 
                     long bufferSize, boolean isVertexBuffer, long maxPoolSize, Set<VkBuffer> globalBufferRegistry) {
        this.arena = arena;
        this.device = device;
        this.physicalDevice = physicalDevice;
        this.bufferSize = bufferSize;
        this.isVertexBuffer = isVertexBuffer;
        this.maxPoolSize = maxPoolSize;
        this.globalBufferRegistry = globalBufferRegistry;
    }
    
    /**
     * Get a buffer from the pool, creating new one if needed
     */
    public VkBuffer acquireBuffer() {
        VkBuffer buffer = availableBuffers.poll();
        if (buffer == null && totalAllocated.get() < maxPoolSize) {
            buffer = createNewBuffer();
            totalAllocated.incrementAndGet();
        }
        
        if (buffer != null) {
            totalUsed.incrementAndGet();
        }
        
        return buffer;
    }
    
    /**
     * Return a buffer to the pool for reuse
     */
    public void releaseBuffer(VkBuffer buffer) {
        if (buffer != null) {
            availableBuffers.offer(buffer);
            totalUsed.decrementAndGet();
        }
    }
    
    private VkBuffer createNewBuffer() {
        VkBuffer.Builder builder = VkBuffer.builder()
            .device(device)
            .physicalDevice(physicalDevice)
            .size(bufferSize)
            .transferDst()
            .deviceLocal();
        
        if (isVertexBuffer) {
            builder.vertexBuffer();
        } else {
            builder.indexBuffer();
        }
        
        // Use a dedicated arena for each buffer to ensure proper lifecycle management
        Arena bufferArena = Arena.ofShared();
        VkBuffer buffer = builder.build(bufferArena);
        
        // Register buffer globally
        globalBufferRegistry.add(buffer);
        
        System.out.println("[DEBUG] Created " + (isVertexBuffer ? "vertex" : "index") + " buffer with handle: 0x" + Long.toHexString(buffer.handle().address()) + ", size: " + bufferSize);
        return buffer;
    }
    
    public long getBufferSize() { return bufferSize; }
    public long getTotalAllocated() { return totalAllocated.get(); }
    public long getTotalUsed() { return totalUsed.get(); }
    public long getAvailableCount() { return availableBuffers.size(); }
    
    /**
     * Pre-allocate buffers to avoid allocation during rendering
     */
    public void preallocate(int count) {
        for (int i = 0; i < count && totalAllocated.get() < maxPoolSize; i++) {
            VkBuffer buffer = createNewBuffer();
            availableBuffers.offer(buffer);
            totalAllocated.incrementAndGet();
        }
    }
    
    public void cleanup() {
        // Just clear the available queue - buffers will be destroyed by BufferManager
        availableBuffers.clear();
    }
    
    public void forceCleanup() {
        cleanup();
    }
}