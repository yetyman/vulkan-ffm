package io.github.yetyman.vulkan.auto;

import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkPhysicalDevice;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

import static io.github.yetyman.vulkan.enums.VkMemoryPropertyFlagBits.*;
import static io.github.yetyman.vulkan.enums.VkStructureType.VK_STRUCTURE_TYPE_MAPPED_MEMORY_RANGE;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkFlushMappedMemoryRanges;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkInvalidateMappedMemoryRanges;

public class MappedBuffer extends AbstractBuffer {
    private MemorySegment mappedMemory;
    private final boolean coherent;
    
    public MappedBuffer(VkDevice device, Arena arena,
                       long size, BufferUsage usage, boolean coherent) {
        super(device, arena, size, usage, 
              coherent ? MemoryStrategy.MAPPED : MemoryStrategy.MAPPED_CACHED);
        this.coherent = coherent;
        
        int memoryProperties = VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT.value();
        if (coherent) {
            memoryProperties |= VK_MEMORY_PROPERTY_HOST_COHERENT_BIT.value();
        } else {
            memoryProperties |= VK_MEMORY_PROPERTY_HOST_CACHED_BIT.value();
        }
        
        createBuffer(usage.toVkFlags(), memoryProperties);
        mapMemory();
    }
    
    private void mapMemory() {
        mappedMemory = vkBuffer.map(arena);
    }
    
    @Override
    public void write(ByteBuffer data, long offset) {
        if (offset + data.remaining() > size) {
            throw new IllegalArgumentException("Write exceeds buffer size");
        }
        
        MemorySegment.copy(MemorySegment.ofBuffer(data), 0, 
                          mappedMemory, offset, data.remaining());
        
        if (!coherent) {
            flush();
        }
    }
    
    @Override
    public ByteBuffer read(long offset, long readSize) {
        if (offset + readSize > size) {
            throw new IllegalArgumentException("Read exceeds buffer size");
        }
        
        if (!coherent) {
            invalidate(offset, readSize);
        }
        
        return mappedMemory.asSlice(offset, readSize).asByteBuffer();
    }
    
    @Override
    public void flush() {
        if (!coherent) {
            MemorySegment range = arena.allocate(40); // VkMappedMemoryRange size
            range.set(java.lang.foreign.ValueLayout.JAVA_INT, 0, VK_STRUCTURE_TYPE_MAPPED_MEMORY_RANGE.value());
            range.set(java.lang.foreign.ValueLayout.ADDRESS, 8, MemorySegment.NULL);
            range.set(java.lang.foreign.ValueLayout.ADDRESS, 16, vkBuffer.memory());
            range.set(java.lang.foreign.ValueLayout.JAVA_LONG, 24, 0L);
            range.set(java.lang.foreign.ValueLayout.JAVA_LONG, 32, size);
            
            vkFlushMappedMemoryRanges(device.handle(), 1, range);
        }
    }
    
    private void invalidate(long offset, long invalidateSize) {
        MemorySegment range = arena.allocate(40); // VkMappedMemoryRange size
        range.set(java.lang.foreign.ValueLayout.JAVA_INT, 0, VK_STRUCTURE_TYPE_MAPPED_MEMORY_RANGE.value());
        range.set(java.lang.foreign.ValueLayout.ADDRESS, 8, MemorySegment.NULL);
        range.set(java.lang.foreign.ValueLayout.ADDRESS, 16, vkBuffer.memory());
        range.set(java.lang.foreign.ValueLayout.JAVA_LONG, 24, offset);
        range.set(java.lang.foreign.ValueLayout.JAVA_LONG, 32, invalidateSize);
        
        vkInvalidateMappedMemoryRanges(device.handle(), 1, range);
    }
    
    @Override
    public void close() {
        super.close();
    }
    
    @Override
    public TransferCompletion writeAsync(ByteBuffer data, long offset) {
        write(data, offset);
        return null; // Mapped buffers are synchronous
    }
}