package io.github.yetyman.vulkan.sample.complex.models;

import io.github.yetyman.vulkan.VkBuffer;
import io.github.yetyman.vulkan.VkResult;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * Host-visible staging buffer for uploading vertex data on background threads
 */
public class StagingBuffer {
    private final VkBuffer buffer;
    private final MemorySegment mappedMemory;
    private final long size;
    private long usedBytes = 0;
    
    public StagingBuffer(Arena arena, MemorySegment device, MemorySegment physicalDevice, long size) {
        this.size = size;
        this.buffer = VkBuffer.builder()
            .device(device)
            .physicalDevice(physicalDevice)
            .size(size)
            .transferSrc()
            .hostVisible()
            .build(Arena.ofShared()); // Force shared arena for staging buffers
        
        // Keep memory mapped for the lifetime of the buffer
        Arena mappingArena = Arena.ofShared();
        MemorySegment mappedPtr = mappingArena.allocate(java.lang.foreign.ValueLayout.ADDRESS);
        
        System.out.println("[STAGING] Mapping memory - device: " + device + ", memory: " + buffer.memory() + ", size: " + size);
        VkResult mapResult = io.github.yetyman.vulkan.VulkanExtensions.mapMemory(device, buffer.memory(), 0, size, 0, mappedPtr);
        mapResult.check();
        
        MemorySegment rawPointer = mappedPtr.get(java.lang.foreign.ValueLayout.ADDRESS, 0);
        this.mappedMemory = rawPointer.reinterpret(size, mappingArena, null);
        System.out.println("[STAGING] Mapped memory segment: " + mappedMemory + ", byteSize: " + mappedMemory.byteSize());
    }
    
    /**
     * Upload vertex data to staging buffer
     * @param data Vertex data to upload
     * @return Offset in staging buffer where data was written, or -1 if no space
     */
    public synchronized long uploadData(MemorySegment data) {
        try {
            if (data.byteSize() > (size - usedBytes)) {
                return -1; // Not enough space
            }
            
            long offset = usedBytes;
            MemorySegment.copy(data, 0, mappedMemory, offset, data.byteSize());
            usedBytes += data.byteSize();
            return offset;
        } catch (Exception e) {
            System.err.println("[STAGING] Failed to upload data: " + e.getMessage());
            return -1;
        }
    }
    
    public VkBuffer getBuffer() { return buffer; }
    public MemorySegment getMappedMemory() { return mappedMemory; }
    public long getSize() { return size; }
    public long getUsedBytes() { return usedBytes; }
    public long getFreeBytes() { return size - usedBytes; }
    
    public synchronized void reset() {
        usedBytes = 0;
    }
    
    public void close() {
        buffer.close();
    }
}