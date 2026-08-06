package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.VkQueue;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

/**
 * Direct memcpy straight into DEVICE_LOCAL | HOST_VISIBLE | HOST_COHERENT (ReBAR) memory.
 * No staging buffer, no persistent-map flush/invalidate — memory is always host coherent.
 */
public final class DirectTransferStrategy implements TransferStrategy {

    @Override
    public GpuCompletion writeAsync(TransferContext ctx, ByteBuffer data, long offset, VkQueue queue) {
        int length = data.remaining();
        try (BufferWriteScope scope = acquireWrite(ctx, offset, length, queue)) {
            MemorySegment.copy(MemorySegment.ofBuffer(data), 0, scope.segment(), 0, length);
        }
        return GpuCompletion.completed();
    }

    @Override
    public BufferWriteScope acquireWrite(TransferContext ctx, long offset, long size, VkQueue queue) {
        if (offset + size > ctx.size) {
            throw new IllegalArgumentException("Write exceeds buffer size");
        }
        // ReBAR memory is always host coherent and device local: the caller's write lands in VRAM
        // directly, so there is nothing to commit on close.
        return BufferWriteScope.of(ctx.mappedMemory.asSlice(offset, size), offset, size, null);
    }

    @Override
    public BufferReadScope acquireRead(TransferContext ctx, long offset, long size, VkQueue queue) {
        if (offset + size > ctx.size) {
            throw new IllegalArgumentException("Read exceeds buffer size");
        }
        return BufferReadScope.of(ctx.mappedMemory.asSlice(offset, size), offset, size, null);
    }

    @Override
    public ByteBuffer read(TransferContext ctx, long offset, long length) {
        if (offset + length > ctx.size) {
            throw new IllegalArgumentException("Read exceeds buffer size");
        }
        return ctx.mappedMemory.asSlice(offset, length).asByteBuffer();
    }

    @Override
    public void flush(TransferContext ctx) {
        // HOST_COHERENT — no explicit flush required
    }
}
