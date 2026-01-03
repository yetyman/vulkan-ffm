package io.github.yetyman.vulkan.highlevel;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.VkMappedMemoryRange;

import java.lang.foreign.*;

/**
 * Persistent mapped buffer for efficient CPU-GPU data transfers.
 * Keeps memory mapped for the lifetime of the buffer to avoid map/unmap overhead.
 * 
 * Example usage:
 * <pre>{@code
 * // Create mapped uniform buffer
 * VkMappedBuffer uniformBuffer = VkMappedBuffer.builder()
 *     .device(device)
 *     .allocator(memoryAllocator)
 *     .size(256)
 *     .uniformBuffer()
 *     .hostVisible()
 *     .build(arena);
 * 
 * // Update data each frame (no map/unmap needed)
 * MemorySegment mvpMatrix = calculateMVP();
 * uniformBuffer.write(0, mvpMatrix);
 * uniformBuffer.flush(); // Only needed for non-coherent memory
 * 
 * // Use in rendering
 * bindBuffer(uniformBuffer.buffer().handle());
 * }</pre>
 */
public class VkMappedBuffer implements AutoCloseable {
    private final VkBuffer buffer;
    private final VkAllocation allocation;
    private final MemorySegment mappedMemory;
    private final VkMemoryAllocator allocator;
    private final long size;
    
    private VkMappedBuffer(VkBuffer buffer, VkAllocation allocation, MemorySegment mappedMemory, 
                          VkMemoryAllocator allocator, long size) {
        this.buffer = buffer;
        this.allocation = allocation;
        this.mappedMemory = mappedMemory;
        this.allocator = allocator;
        this.size = size;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    /** @return the underlying VkBuffer */
    public VkBuffer buffer() { return buffer; }
    
    /** @return the buffer size in bytes */
    public long size() { return size; }
    
    /** @return the mapped memory segment for CPU access */
    public MemorySegment mappedMemory() { return mappedMemory; }
    
    /**
     * Writes data to the buffer at the specified offset.
     */
    public void write(long offset, MemorySegment data) {
        if (offset + data.byteSize() > size) {
            throw new IllegalArgumentException("Write would exceed buffer size");
        }
        MemorySegment.copy(data, 0, mappedMemory, offset, data.byteSize());
    }
    
    /**
     * Writes data to the buffer starting at offset 0.
     */
    public void write(MemorySegment data) {
        write(0, data);
    }
    
    /**
     * Reads data from the buffer at the specified offset.
     */
    public void read(long offset, MemorySegment destination) {
        if (offset + destination.byteSize() > size) {
            throw new IllegalArgumentException("Read would exceed buffer size");
        }
        MemorySegment.copy(mappedMemory, offset, destination, 0, destination.byteSize());
    }
    
    /**
     * Flushes mapped memory to ensure writes are visible to the GPU.
     * Only needed for non-coherent memory.
     */
    public void flush() {
        flush(0, size);
    }
    
    /**
     * Flushes a specific range of mapped memory.
     */
    public void flush(long offset, long size) {
        MemorySegment flushRange = Arena.ofAuto().allocate(VkMappedMemoryRange.layout());
        VkMappedMemoryRange.sType(flushRange, VkStructureType.VK_STRUCTURE_TYPE_MAPPED_MEMORY_RANGE);
        VkMappedMemoryRange.memory(flushRange, allocation.memory());
        VkMappedMemoryRange.offset(flushRange, allocation.offset() + offset);
        VkMappedMemoryRange.size(flushRange, size);
        
        Vulkan.flushMappedMemoryRanges(buffer.device().handle(), 1, flushRange).check();
    }
    
    /**
     * Invalidates mapped memory to ensure GPU writes are visible to the CPU.
     * Only needed for non-coherent memory.
     */
    public void invalidate() {
        invalidate(0, size);
    }
    
    /**
     * Invalidates a specific range of mapped memory.
     */
    public void invalidate(long offset, long size) {
        MemorySegment invalidateRange = Arena.ofAuto().allocate(VkMappedMemoryRange.layout());
        VkMappedMemoryRange.sType(invalidateRange, VkStructureType.VK_STRUCTURE_TYPE_MAPPED_MEMORY_RANGE);
        VkMappedMemoryRange.memory(invalidateRange, allocation.memory());
        VkMappedMemoryRange.offset(invalidateRange, allocation.offset() + offset);
        VkMappedMemoryRange.size(invalidateRange, size);
        
        Vulkan.invalidateMappedMemoryRanges(buffer.device().handle(), 1, invalidateRange).check();
    }
    
    @Override
    public void close() {
        if (allocator != null) {
            allocator.unmap(allocation);
            allocator.free(allocation);
        }
        if (buffer != null) {
            buffer.close();
        }
    }
    
    public static class Builder {
        private VkDevice device;
        private VkMemoryAllocator allocator;
        private long size;
        private int usage = VkBufferUsageFlagBits.VK_BUFFER_USAGE_TRANSFER_SRC_BIT;
        private int memoryProperties = VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | 
                                      VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
        private MemorySegment initialData;
        
        public Builder device(VkDevice device) {
            this.device = device;
            return this;
        }
        
        public Builder allocator(VkMemoryAllocator allocator) {
            this.allocator = allocator;
            return this;
        }
        
        public Builder size(long size) {
            this.size = size;
            return this;
        }
        
        public Builder usage(int usage) {
            this.usage = usage;
            return this;
        }
        
        public Builder vertexBuffer() {
            this.usage |= VkBufferUsageFlagBits.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
            return this;
        }
        
        public Builder indexBuffer() {
            this.usage |= VkBufferUsageFlagBits.VK_BUFFER_USAGE_INDEX_BUFFER_BIT;
            return this;
        }
        
        public Builder uniformBuffer() {
            this.usage |= VkBufferUsageFlagBits.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT;
            return this;
        }
        
        public Builder storageBuffer() {
            this.usage |= VkBufferUsageFlagBits.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
            return this;
        }
        
        public Builder transferSrc() {
            this.usage |= VkBufferUsageFlagBits.VK_BUFFER_USAGE_TRANSFER_SRC_BIT;
            return this;
        }
        
        public Builder transferDst() {
            this.usage |= VkBufferUsageFlagBits.VK_BUFFER_USAGE_TRANSFER_DST_BIT;
            return this;
        }
        
        public Builder hostVisible() {
            this.memoryProperties = VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | 
                                   VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
            return this;
        }
        
        public Builder hostCached() {
            this.memoryProperties = VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | 
                                   VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_CACHED_BIT;
            return this;
        }
        
        public Builder initialData(MemorySegment data) {
            this.initialData = data;
            return this;
        }
        
        public VkMappedBuffer build(Arena arena) {
            if (device == null) throw new IllegalStateException("device not set");
            if (allocator == null) throw new IllegalStateException("allocator not set");
            if (size <= 0) throw new IllegalStateException("invalid size");
            
            VkBuffer buffer = VkBuffer.builder()
                .device(device)
                .size(size)
                .usage(usage)
                .build(arena);
            
            // Allocate memory
            VkAllocation allocation = allocator.allocateBuffer(buffer.handle(), memoryProperties);
            
            // Bind memory
            Vulkan.bindBufferMemory(device.handle(), buffer.handle(), allocation.memory(), allocation.offset()).check();
            
            // Map memory
            MemorySegment mappedMemory = allocator.map(allocation);
            
            // Upload initial data if provided
            if (initialData != null && initialData.byteSize() > 0) {
                long copySize = Math.min(size, initialData.byteSize());
                MemorySegment.copy(initialData, 0, mappedMemory, 0, copySize);
            }
            
            return new VkMappedBuffer(buffer, allocation, mappedMemory, allocator, size);
        }
    }
}