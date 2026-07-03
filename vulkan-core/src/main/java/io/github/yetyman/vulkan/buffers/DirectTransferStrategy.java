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
    public TransferCompletion writeAsync(TransferContext ctx, ByteBuffer data, long offset, VkQueue queue) {
        if (offset + data.remaining() > ctx.size) {
            throw new IllegalArgumentException("Write exceeds buffer size");
        }
        MemorySegment.copy(MemorySegment.ofBuffer(data), 0, ctx.mappedMemory, offset, data.remaining());
        return TransferCompletion.completed();
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
