package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkMappedMemoryRange;
import io.github.yetyman.vulkan.VkPhysicalDevice;
import io.github.yetyman.vulkan.VkQueue;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

import static io.github.yetyman.vulkan.enums.VkMemoryPropertyFlagBits.*;
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
            MemorySegment range = VkMappedMemoryRange.allocate(arena, vkBuffer.memory(), 0, size);
            vkFlushMappedMemoryRanges(device.handle(), 1, range);
        }
    }
    
    private void invalidate(long offset, long invalidateSize) {
        long atomSize = physicalDevice.getNonCoherentAtomSize();
        long alignedOffset = (offset / atomSize) * atomSize;
        long alignedEnd = offset + invalidateSize;
        // round end up to atom boundary unless it reaches the end of the allocation
        if (alignedEnd < size) alignedEnd = ((alignedEnd + atomSize - 1) / atomSize) * atomSize;
        MemorySegment range = VkMappedMemoryRange.allocate(arena, vkBuffer.memory(), alignedOffset, alignedEnd - alignedOffset);
        vkInvalidateMappedMemoryRanges(device.handle(), 1, range);
    }
    
    @Override
    public void closeImpl() {
        // mappedMemory unmapped automatically by arena cleanup
        super.closeImpl();
    }
    
    @Override
    public TransferCompletion writeAsync(ByteBuffer data, long offset) {
        write(data, offset);
        return TransferCompletion.completed();
    }
}