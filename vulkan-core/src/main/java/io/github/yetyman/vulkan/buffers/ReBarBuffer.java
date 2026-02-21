package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkMappedMemoryRange;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

import static io.github.yetyman.vulkan.enums.VkMemoryPropertyFlagBits.*;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkFlushMappedMemoryRanges;

/**
 * Buffer backed by DEVICE_LOCAL | HOST_VISIBLE memory (Resizable BAR / Smart Access Memory).
 * Writes go directly into VRAM without a staging buffer or copy command.
 * Write-combining on the CPU side makes sequential writes efficient; random reads are slow.
 * Only instantiate when VulkanCapabilities.reBar is true.
 */
public class ReBarBuffer extends AbstractBuffer {
    private final MemorySegment mappedMemory;

    public ReBarBuffer(VkDevice device, Arena arena, long size, BufferUsage usage) {
        super(device, arena, size, usage, MemoryStrategy.REBAR);
        createBuffer(usage.toVkFlags(),
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT.value()
              | VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT.value()
              | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT.value());
        mappedMemory = vkBuffer.map(arena);
    }

    @Override
    public void write(ByteBuffer data, long offset) {
        if (offset + data.remaining() > size) {
            throw new IllegalArgumentException("Write exceeds buffer size");
        }
        MemorySegment.copy(MemorySegment.ofBuffer(data), 0, mappedMemory, offset, data.remaining());
    }

    @Override
    public TransferCompletion writeAsync(ByteBuffer data, long offset) {
        write(data, offset);
        return TransferCompletion.completed();
    }

    @Override
    public ByteBuffer read(long offset, long readSize) {
        if (offset + readSize > size) {
            throw new IllegalArgumentException("Read exceeds buffer size");
        }
        return mappedMemory.asSlice(offset, readSize).asByteBuffer();
    }

    @Override
    public void flush() {
        // HOST_COHERENT — no explicit flush required
    }
}
