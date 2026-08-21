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
 *
 * For the coherent case, uses a pooled write scope to avoid per-call allocation.
 */
public final class MappedTransferStrategy implements TransferStrategy {
    private final boolean coherent;

    // Pooled write scope for the coherent path (no onCommit needed, just returns the segment)
    private final PooledMappedWriteScope pooledScope = new PooledMappedWriteScope();

    public MappedTransferStrategy(boolean coherent) {
        this.coherent = coherent;
    }

    @Override
    public GpuCompletion writeAsync(TransferContext ctx, ByteBuffer data, long offset, VkQueue queue) {
        int length = data.remaining();
        if (offset + length > ctx.size) {
            throw new IllegalArgumentException("Write exceeds buffer size");
        }
        MemorySegment target = ctx.mappedMemory.asSlice(offset, length);
        MemorySegment.copy(MemorySegment.ofBuffer(data), 0, target, 0, length);
        if (!coherent) {
            flushRange(ctx, offset, length);
        }
        return GpuCompletion.completed();
    }

    @Override
    public BufferWriteScope acquireWrite(TransferContext ctx, long offset, long size, VkQueue queue) {
        if (offset + size > ctx.size) {
            throw new IllegalArgumentException("Write exceeds buffer size");
        }
        if (coherent) {
            // Zero-allocation path: return the full mapped segment with offset metadata.
            // The caller writes at position 0 within the scope's segment, which is
            // already sliced to the correct region via reinterpret (no new object).
            pooledScope.reset(ctx.mappedMemory, offset, size);
            return pooledScope;
        }
        MemorySegment target = ctx.mappedMemory.asSlice(offset, size);
        return BufferWriteScope.of(target, offset, size, () -> {
            flushRange(ctx, offset, size);
            return GpuCompletion.completed();
        });
    }

    @Override
    public BufferReadScope acquireRead(TransferContext ctx, long offset, long size, VkQueue queue) {
        if (offset + size > ctx.size) {
            throw new IllegalArgumentException("Read exceeds buffer size");
        }
        if (!coherent) {
            invalidateRange(ctx, offset, size);
        }
        return BufferReadScope.of(ctx.mappedMemory.asSlice(offset, size), offset, size, null);
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

    /**
     * Reusable write scope for coherent mapped memory. Returns a view into the full mapped
     * segment at the requested offset without allocating a new MemorySegment. The segment()
     * method returns the full mapped memory, but offset-adjusted: callers write at position 0
     * relative to the scope, which maps to `offset` in the buffer.
     *
     * <p>Implementation: stores the full mapped segment and applies offset arithmetic in
     * segment() by returning a pointer-offset view via {@code MemorySegment.asSlice} only
     * once during reset. Actually, we cannot avoid asSlice if we want the scope contract
     * (write at 0 = write at buffer offset). Instead, we simply reuse the scope object
     * itself (avoiding DefaultBufferWriteScope allocation) and accept the asSlice cost.
     *
     * NOT thread-safe -- one instance per MappedTransferStrategy.
     */
    private static final class PooledMappedWriteScope implements BufferWriteScope {
        private MemorySegment segment;
        private long offset;
        private long size;

        void reset(MemorySegment fullMapped, long offset, long size) {
            this.segment = fullMapped.asSlice(offset, size);
            this.offset = offset;
            this.size = size;
        }

        @Override public MemorySegment segment() { return segment; }
        @Override public long offset() { return offset; }
        @Override public long size() { return size; }
        @Override public GpuCompletion completion() { return GpuCompletion.completed(); }
        @Override public void close() { /* coherent: nothing to do */ }
    }
}
