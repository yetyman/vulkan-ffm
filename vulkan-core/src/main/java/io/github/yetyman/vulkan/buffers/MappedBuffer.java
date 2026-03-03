package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkMappedMemoryRange;
import io.github.yetyman.vulkan.VkQueue;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

import static io.github.yetyman.vulkan.enums.VkMemoryPropertyFlagBits.*;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkFlushMappedMemoryRanges;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkInvalidateMappedMemoryRanges;

public class MappedBuffer extends AbstractBuffer {
    private MemorySegment mappedMemory;
    private final boolean coherent;

    public MappedBuffer(VkDevice device, long size, BufferUsage usage, boolean coherent) {
        super(device, size, usage, coherent ? MemoryStrategy.MAPPED : MemoryStrategy.MAPPED_CACHED);
        this.coherent = coherent;

        int memoryProperties = VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT.value();
        if (coherent) {
            memoryProperties |= VK_MEMORY_PROPERTY_HOST_COHERENT_BIT.value();
        } else {
            memoryProperties |= VK_MEMORY_PROPERTY_HOST_CACHED_BIT.value();
        }

        try {
            createBuffer(usage.toVkFlags(), memoryProperties);
            mappedMemory = vkBuffer.map(arena);
        } catch (Exception e) {
            if (vkBuffer != null) vkBuffer.close();
            arena.close();
            throw e;
        }
    }

    public void write(ByteBuffer data, long offset) {
        if (offset + data.remaining() > size) {
            throw new IllegalArgumentException("Write exceeds buffer size");
        }
        MemorySegment.copy(MemorySegment.ofBuffer(data), 0, mappedMemory, offset, data.remaining());
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
        if (alignedEnd < size) alignedEnd = ((alignedEnd + atomSize - 1) / atomSize) * atomSize;
        MemorySegment range = VkMappedMemoryRange.allocate(arena, vkBuffer.memory(), alignedOffset, alignedEnd - alignedOffset);
        vkInvalidateMappedMemoryRanges(device.handle(), 1, range);
    }

    @Override
    protected void closeImpl() {
        // mappedMemory unmapped automatically when arena closes (in AbstractBuffer.close())
        super.closeImpl();
    }

    public TransferCompletion writeAsync(ByteBuffer data, long offset) {
        write(data, offset);
        return TransferCompletion.completed();
    }

    @Override
    public TransferCompletion writeAsync(ByteBuffer data, long offset, VkQueue queue) {
        return writeAsync(data, offset);
    }
}
