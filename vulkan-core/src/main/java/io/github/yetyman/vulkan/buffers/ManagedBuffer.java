package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.VkBuffer;
import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkQueue;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Primary {@link IBuffer} implementation, composing an {@link AllocationStrategy} (where the
 * memory lives and how it is mapped) with a {@link TransferStrategy} (how data actually moves
 * between CPU and GPU). These two concerns are fully orthogonal — any allocation strategy can
 * be paired with any transfer strategy that its capabilities support.
 *
 * <p>Constructed via {@link #builder()} for direct strategy composition, or more commonly via
 * {@link BufferFactory}, which selects strategies from a {@link MemoryStrategy} enum value.
 *
 * <p>When the allocation strategy is a {@link SparseAllocationStrategy}, this instance also
 * implements {@link SparseCapable} for page-level commit/decommit control.
 */
public final class ManagedBuffer implements IBuffer, SparseCapable {
    private final VkDevice device;
    private final Arena arena;
    private final long size;
    private final BufferUsage usage;
    private final AllocationStrategy allocation;
    private final TransferStrategy transfer;
    private final VkBuffer vkBuffer;
    private final TransferContext context;

    private final AtomicBoolean closed = new AtomicBoolean(false);

    private ManagedBuffer(VkDevice device, long size, BufferUsage usage,
                          AllocationStrategy allocation, TransferStrategy transfer) {
        this.device = device;
        this.size = size;
        this.usage = usage;
        this.allocation = allocation;
        this.transfer = transfer;
        this.arena = Arena.ofShared();

        try {
            this.vkBuffer = allocation.allocate(device, size, usage.toVkFlags(), arena);
            MemorySegment mapped = allocation.persistentMap(device, vkBuffer, arena);
            SparsePageAllocator sparsePages = allocation instanceof SparseAllocationStrategy sparse ? sparse.pages() : null;
            this.context = new TransferContext(device, arena, vkBuffer, size, mapped, sparsePages);
        } catch (Exception e) {
            arena.close();
            throw e;
        }
    }

    /**
     * @return a new builder for directly composing an allocation and transfer strategy.
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public MemorySegment handle() {
        return vkBuffer.handle();
    }

    /**
     * @return the underlying VkBuffer. Needed for Vulkan usage-flag validation at descriptor
     * bind time — not exposed on {@link IBuffer} since arbitrary custom implementers may not own one.
     */
    public VkBuffer vkBuffer() {
        return vkBuffer;
    }

    @Override
    public long size() {
        return size;
    }

    @Override
    public BufferUsage usage() {
        return usage;
    }

    /**
     * @return the allocation strategy backing this buffer.
     */
    public AllocationStrategy allocationStrategy() {
        return allocation;
    }

    /**
     * @return the transfer strategy backing this buffer.
     */
    public TransferStrategy transferStrategy() {
        return transfer;
    }

    @Override
    public void write(ByteBuffer data, long offset, VkQueue queue) {
        GpuCompletion tc = writeAsync(data, offset, queue);
        TransferBatchManager.flush(device, queue);
        tc.await();
        tc.close();
    }

    @Override
    public GpuCompletion writeAsync(ByteBuffer data, long offset, VkQueue queue) {
        return transfer.writeAsync(context, data, offset, queue);
    }

    @Override
    public BufferWriteScope acquireWrite(long offset, long size, VkQueue queue) {
        return transfer.acquireWrite(context, offset, size, queue);
    }

    @Override
    public BufferReadScope acquireRead(long offset, long size, VkQueue queue) {
        return transfer.acquireRead(context, offset, size, queue);
    }

    @Override
    public ByteBuffer read(long offset, long length) {
        return transfer.read(context, offset, length);
    }

    @Override
    public void flush() {
        transfer.flush(context);
    }

    @Override
    public void copyTo(IBuffer dst, long srcOffset, long dstOffset, long length, VkQueue queue) {
        GpuCompletion tc = copyToAsync(dst, srcOffset, dstOffset, length, queue);
        TransferBatchManager.flush(device, queue);
        tc.await();
        tc.close();
    }

    @Override
    public GpuCompletion copyToAsync(IBuffer dst, long srcOffset, long dstOffset, long length, VkQueue queue) {
        TransferBatch batch = TransferBatchManager.getOrCreate(device, queue);
        return batch.record(handle(), dst.handle(), srcOffset, dstOffset, length);
    }

    /**
     * Issues a GPU copy from an externally-owned buffer handle directly into this buffer,
     * without staging the data through this buffer's own {@link TransferStrategy}.
     *
     * <p>Narrow optimization entry point: for callers (e.g. {@link MirroredBuffer}) that already
     * hold data in their own mapped, host-visible {@code VkBuffer} and only need the GPU-side
     * copy — calling {@link #writeAsync} instead would redundantly re-stage the same bytes.
     *
     * @param srcHandle source VkBuffer handle — must be host-visible and TRANSFER_SRC capable
     */
    public GpuCompletion copyFromExternal(MemorySegment srcHandle, long srcOffset, long dstOffset, long length, VkQueue queue) {
        TransferBatch batch = TransferBatchManager.getOrCreate(device, queue);
        return batch.record(srcHandle, handle(), srcOffset, dstOffset, length);
    }

    /**
     * Issues a GPU copy from this buffer directly into an externally-owned buffer handle,
     * without routing through the destination's own {@link TransferStrategy}.
     *
     * <p>Narrow optimization entry point: the counterpart to {@link #copyFromExternal} — used by
     * {@link MirroredBuffer#refreshFromGpu} to read this buffer's current GPU-side contents
     * straight into its own mapped mirror memory.
     *
     * @param dstHandle destination VkBuffer handle — must be host-visible and TRANSFER_DST capable
     */
    public GpuCompletion copyToExternal(MemorySegment dstHandle, long srcOffset, long dstOffset, long length, VkQueue queue) {
        TransferBatch batch = TransferBatchManager.getOrCreate(device, queue);
        return batch.record(handle(), dstHandle, srcOffset, dstOffset, length);
    }

    // -------------------------------------------------------------------------
    // SparseCapable — functional only when allocationStrategy() is SparseAllocationStrategy
    // -------------------------------------------------------------------------

    @Override
    public long pageSize() {
        return requireSparsePages().pageSize;
    }

    @Override
    public void commitPages(long offset, long length) {
        requireSparsePages().ensurePagesCommitted(offset, length);
    }

    @Override
    public void decommitPages(long offset, long length) {
        requireSparsePages().decommitPages(offset, length);
    }

    @Override
    public boolean isCommitted(long offset, long length) {
        try {
            requireSparsePages().validatePagesCommitted(offset, length);
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private SparsePageAllocator requireSparsePages() {
        if (context.sparsePages == null)
            throw new UnsupportedOperationException(getClass().getSimpleName() + " is not backed by a sparse allocation strategy");
        return context.sparsePages;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                transfer.close(context);
                if (context.sparsePages != null) context.sparsePages.close();
                if (vkBuffer != null) vkBuffer.close();
            } finally {
                arena.close();
            }
        }
    }

    /**
     * Fluent builder for directly composing an {@link AllocationStrategy} and
     * {@link TransferStrategy}. Prefer {@link BufferFactory} for the common cases —
     * this builder is for callers that need explicit control over the composition.
     */
    public static class Builder {
        private VkDevice device;
        private long size;
        private BufferUsage usage;
        private AllocationStrategy allocation;
        private TransferStrategy transfer;

        private Builder() {
        }

        /** Sets the logical device. */
        public Builder device(VkDevice device) {
            this.device = device;
            return this;
        }

        /** Sets the buffer size in bytes. */
        public Builder size(long size) {
            this.size = size;
            return this;
        }

        /** Sets the Vulkan buffer usage (UBO/SSBO/VBO/etc). */
        public Builder usage(BufferUsage usage) {
            this.usage = usage;
            return this;
        }

        /** Sets the allocation strategy — where the memory lives and how it is mapped. */
        public Builder allocation(AllocationStrategy allocation) {
            this.allocation = allocation;
            return this;
        }

        /** Sets the transfer strategy — how data moves between CPU and GPU. */
        public Builder transfer(TransferStrategy transfer) {
            this.transfer = transfer;
            return this;
        }

        public ManagedBuffer build() {
            if (device == null) throw new IllegalStateException("device not set");
            if (size <= 0) throw new IllegalStateException("invalid size");
            if (usage == null) throw new IllegalStateException("usage not set");
            if (allocation == null) throw new IllegalStateException("allocation strategy not set");
            if (transfer == null) throw new IllegalStateException("transfer strategy not set");
            return new ManagedBuffer(device, size, usage, allocation, transfer);
        }
    }
}
