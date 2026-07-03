package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.VkMappedMemoryRange;
import io.github.yetyman.vulkan.VkQueue;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

import static io.github.yetyman.vulkan.generated.VulkanFFM.vkFlushMappedMemoryRanges;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkInvalidateMappedMemoryRanges;

/**
 * Direct memcpy into a persistently-mapped buffer, no staging, no copy commands.
 * When {@code coherent} is false, writes flush and reads invalidate the non-coherent
 * memory range (aligned to the device's non-coherent atom size).
 */
public final class MappedTransferStrategy implements TransferStrategy {
    private final boolean coherent;

    public MappedTransferStrategy(boolean coherent) {
        this.coherent = coherent;
    }

    @Override
    public TransferCompletion writeAsync(TransferContext ctx, ByteBuffer data, long offset, VkQueue queue) {
        if (offset + data.remaining() > ctx.size) {
            throw new IllegalArgumentException("Write exceeds buffer size");
        }
        MemorySegment.copy(MemorySegment.ofBuffer(data), 0, ctx.mappedMemory, offset, data.remaining());
        if (!coherent) {
            flushRange(ctx, offset, data.remaining());
        }
        return TransferCompletion.completed();
    }

    @Override
    public ByteBuffer read(TransferContext ctx, long offset, long length) {
        if (offset + length > ctx.size) {
            throw new IllegalArgumentException("Read exceeds buffer size");
        }
        if (!coherent) {
            invalidateRange(ctx, offset, length);
        }
        return ctx.mappedMemory.asSlice(offset, length).asByteBuffer();
    }

    @Override
    public void flush(TransferContext ctx) {
        if (!coherent) {
            flushRange(ctx, 0, ctx.size);
        }
    }

    private void flushRange(TransferContext ctx, long offset, long length) {
        long atomSize = ctx.physicalDevice.getNonCoherentAtomSize();
        long alignedOffset = (offset / atomSize) * atomSize;
        long alignedEnd = offset + length;
        if (alignedEnd < ctx.size) alignedEnd = ((alignedEnd + atomSize - 1) / atomSize) * atomSize;
        else alignedEnd = ctx.size;
        MemorySegment range = VkMappedMemoryRange.allocate(ctx.arena, ctx.vkBuffer.memory(), alignedOffset, alignedEnd - alignedOffset);
        vkFlushMappedMemoryRanges(ctx.device.handle(), 1, range);
    }

    private void invalidateRange(TransferContext ctx, long offset, long length) {
        long atomSize = ctx.physicalDevice.getNonCoherentAtomSize();
        long alignedOffset = (offset / atomSize) * atomSize;
        long alignedEnd = offset + length;
        if (alignedEnd < ctx.size) alignedEnd = ((alignedEnd + atomSize - 1) / atomSize) * atomSize;
        MemorySegment range = VkMappedMemoryRange.allocate(ctx.arena, ctx.vkBuffer.memory(), alignedOffset, alignedEnd - alignedOffset);
        vkInvalidateMappedMemoryRanges(ctx.device.handle(), 1, range);
    }
}
