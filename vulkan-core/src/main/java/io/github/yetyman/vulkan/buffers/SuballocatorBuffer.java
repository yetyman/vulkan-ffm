package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.VkBuffer;
import io.github.yetyman.vulkan.VkCommandPool;
import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkQueue;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Suballocator buffer that manages multiple small allocations within a single large VkBuffer.
 * 
 * <p>This is highly efficient for uniform buffers and small storage buffers because:
 * <ul>
 *   <li>Reduces VkBuffer allocation overhead (each VkBuffer has kernel-side metadata)</li>
 *   <li>Improves memory locality and cache efficiency</li>
 *   <li>Supports dynamic offsets in descriptors (VkDescriptorBufferInfo.offset)</li>
 *   <li>Minimizes memory fragmentation</li>
 * </ul>
 * 
 * <p><b>Descriptor Usage:</b>
 * When binding suballocations to descriptors, use the base buffer handle with dynamic offsets:
 * <pre>{@code
 * SuballocatorBuffer allocator = new SuballocatorBuffer(...);
 * SuballocatorBuffer.Suballocation alloc1 = allocator.allocate(256);
 * SuballocatorBuffer.Suballocation alloc2 = allocator.allocate(256);
 * 
 * // In descriptor set:
 * VkDescriptorBufferInfo bufferInfo1 = {
 *     .buffer = allocator.handle(),  // Same buffer for all
 *     .offset = alloc1.offset(),     // Different offset per allocation
 *     .range = alloc1.size()
 * };
 * 
 * // Or use dynamic offsets at bind time:
 * vkCmdBindDescriptorSets(..., dynamicOffsetCount=2, 
 *     pDynamicOffsets=[alloc1.offset(), alloc2.offset()]);
 * }</pre>
 * 
 * <p><b>Alignment:</b> All suballocations are aligned to minUniformBufferOffsetAlignment
 * or minStorageBufferOffsetAlignment as required by Vulkan spec.
 */
public class SuballocatorBuffer extends AbstractBuffer {
    private final ManagedBuffer backingBuffer;
    private final List<Suballocation> allocations = new ArrayList<>();
    private final long alignment;
    private long currentOffset = 0;
    
    public SuballocatorBuffer(VkDevice device, Arena arena,
                             long totalSize, BufferUsage usage, MemoryStrategy backingStrategy,
                             VkQueue transferQueue, VkCommandPool commandPool) {
        super(device, arena, totalSize, usage, MemoryStrategy.SUBALLOCATOR);
        
        // Get alignment requirement from device
        this.alignment = getAlignmentForUsage(device, usage);
        
        // Create backing buffer
        this.backingBuffer = BufferFactory.create(
            backingStrategy, MemoryStrategy.DEVICE_LOCAL,
            totalSize, usage, device, transferQueue, commandPool, arena
        );
    }
    
    private long getAlignmentForUsage(VkDevice device, BufferUsage usage) {
        // Get device limits
        long minUniformAlignment = device.physicalDevice().getMinUniformBufferOffsetAlignment();
        long minStorageAlignment = device.physicalDevice().getMinStorageBufferOffsetAlignment();
        
        return switch (usage) {
            case UNIFORM -> minUniformAlignment;
            case STORAGE -> minStorageAlignment;
            case VERTEX -> 4; // Vertex attributes typically 4-byte aligned
            case TRANSFER -> 1; // No special alignment
            case MIXED -> Math.max(minUniformAlignment, minStorageAlignment);
        };
    }
    
    /**
     * Allocates a suballocation of the specified size.
     * Returns null if insufficient space remains.
     */
    public synchronized Suballocation allocate(long size) {
        // Align current offset
        long alignedOffset = alignUp(currentOffset, alignment);
        
        // Check if we have space
        if (alignedOffset + size > this.size) {
            return null; // Out of space
        }
        
        Suballocation alloc = new Suballocation(alignedOffset, size);
        allocations.add(alloc);
        currentOffset = alignedOffset + size;
        
        return alloc;
    }
    
    /**
     * Frees a suballocation. Note: This does NOT compact memory.
     * For true compaction, recreate the SuballocatorBuffer.
     */
    public synchronized void free(Suballocation alloc) {
        allocations.remove(alloc);
        // Note: We don't reclaim space - this is a simple allocator
        // For production, consider implementing a free list or buddy allocator
    }
    
    /**
     * Returns the backing buffer handle for descriptor binding.
     */
    @Override
    public MemorySegment handle() {
        return backingBuffer.handle();
    }
    
    @Override
    public void write(ByteBuffer data, long offset) {
        backingBuffer.write(data, offset);
    }
    
    @Override
    public TransferCompletion writeAsync(ByteBuffer data, long offset) {
        return backingBuffer.writeAsync(data, offset);
    }
    
    @Override
    public ByteBuffer read(long offset, long size) {
        return backingBuffer.read(offset, size);
    }
    
    @Override
    public void flush() {
        backingBuffer.flush();
    }
    
    @Override
    public void closeImpl() {
        backingBuffer.close();
    }
    
    private static long alignUp(long value, long alignment) {
        return (value + alignment - 1) & ~(alignment - 1);
    }
    
    /**
     * Represents a suballocation within the backing buffer.
     * Use offset() for descriptor binding and write operations.
     */
    public class Suballocation {
        private final long offset;
        private final long size;
        
        private Suballocation(long offset, long size) {
            this.offset = offset;
            this.size = size;
        }
        
        /**
         * Returns the offset within the backing buffer.
         * Use this for VkDescriptorBufferInfo.offset or dynamic offsets.
         */
        public long offset() {
            return offset;
        }
        
        /**
         * Returns the size of this suballocation.
         */
        public long size() {
            return size;
        }
        
        /**
         * Writes data to this suballocation.
         */
        public void write(ByteBuffer data) {
            if (data.remaining() > size) {
                throw new IllegalArgumentException("Data exceeds suballocation size");
            }
            SuballocatorBuffer.this.write(data, offset);
        }
        
        /**
         * Writes data to this suballocation asynchronously.
         */
        public TransferCompletion writeAsync(ByteBuffer data) {
            if (data.remaining() > size) {
                throw new IllegalArgumentException("Data exceeds suballocation size");
            }
            return SuballocatorBuffer.this.writeAsync(data, offset);
        }
        
        /**
         * Reads data from this suballocation.
         */
        public ByteBuffer read() {
            return SuballocatorBuffer.this.read(offset, size);
        }
        
        /**
         * Frees this suballocation.
         */
        public void free() {
            SuballocatorBuffer.this.free(this);
        }
    }
}
