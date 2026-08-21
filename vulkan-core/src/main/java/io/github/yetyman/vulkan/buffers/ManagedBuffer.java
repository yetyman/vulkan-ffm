package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.VkBuffer;
import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkQueue;
import io.github.yetyman.vulkan.command.VkCopy;

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
    private final CpuObservability observability;
    private final DirtyStrategy cpuDirty;
    private final DirtyStrategy gpuDirty;
    private final VkBuffer vkBuffer;
    private final TransferContext context;
    private volatile boolean deferred;

    private final AtomicBoolean closed = new AtomicBoolean(false);

    private ManagedBuffer(VkDevice device, long size, BufferUsage usage,
                          AllocationStrategy allocation, TransferStrategy transfer,
                          CpuObservability observability, DirtyStrategy cpuDirty,
                          DirtyStrategy gpuDirty) {
        this.device = device;
        this.size = size;
        this.usage = usage;
        this.allocation = allocation;
        this.transfer = transfer;
        this.observability = observability;
        this.cpuDirty = cpuDirty;
        this.gpuDirty = gpuDirty;
        this.deferred = false;
        this.arena = Arena.ofShared();

        try {
            this.vkBuffer = allocation.allocate(device, size, usage.toVkFlags(), arena);
            MemorySegment mapped = allocation.persistentMap(device, vkBuffer, arena);
            SparsePageAllocator sparsePages = allocation instanceof SparseAllocationStrategy sparse ? sparse.pages() : null;
            this.context = new TransferContext(device, arena, vkBuffer, size, mapped, sparsePages);

            // Initialize observability with the primary buffer's handle
            observability.initialize(device, size, vkBuffer.handle(), arena);

            // If observability is inherent (mapped memory), feed it the mapped segment
            if (observability instanceof InherentObservability inherent && mapped != null) {
                inherent.setMappedMemory(mapped);
            }
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

    /**
     * @return the CPU observability strategy for this buffer.
     */
    public CpuObservability observability() {
        return observability;
    }

    /**
     * @return the CPU->GPU dirty tracking strategy.
     */
    public DirtyStrategy cpuDirtyStrategy() {
        return cpuDirty;
    }

    /**
     * @return the GPU->CPU dirty tracking strategy.
     */
    public DirtyStrategy gpuDirtyStrategy() {
        return gpuDirty;
    }

    // -------------------------------------------------------------------------
    // Deferred mode and dirty flush
    // -------------------------------------------------------------------------

    /**
     * Sets whether writes accumulate (true) or immediately transfer (false).
     * Setting to true triggers an async flush of any pending dirty state (typically a no-op
     * since immediate mode clears dirty state on each write).
     */
    public void setDeferred(boolean deferred) {
        if (this.deferred == deferred) return;
        if (deferred && cpuDirty.isDirty()) {
            // Flush any accumulated state before switching to deferred mode
            flushDirtyAsync(null);
        }
        this.deferred = deferred;
    }

    /**
     * @return true if writes accumulate without immediate GPU transfer
     */
    public boolean isDeferred() {
        return deferred;
    }

    /**
     * Flushes all CPU-dirty ranges to the GPU using multi-region copy.
     * No-op if nothing is dirty or if observability is not mirrored.
     * In immediate mode (deferred == false), always a no-op since writes already transferred.
     *
     * @param queue the queue to issue the copy on (uses transfer queue if null)
     * @return a completion for the flush, or an already-complete token if nothing to flush
     */
    public GpuCompletion flushDirty(VkQueue queue) {
        GpuCompletion completion = flushDirtyAsync(queue);
        if (completion != GpuCompletion.completed()) {
            TransferBatchManager.flush(device, queue);
            completion.await();
            completion.close();
        }
        return GpuCompletion.completed();
    }

    /**
     * Async version of {@link #flushDirty(VkQueue)}: records dirty-range copies into the
     * transfer batch, returns immediately. Caller must flush the batch and await.
     *
     * @param queue the queue to issue the copy on
     * @return a completion for the flush, or an already-complete token if nothing to flush
     */
    public GpuCompletion flushDirtyAsync(VkQueue queue) {
        if (!cpuDirty.isDirty()) return GpuCompletion.completed();
        if (!(observability instanceof MirrorCapable mirror)) return GpuCompletion.completed();

        int regionCount = cpuDirty.dirtyRegionCount();
        if (regionCount == 0) return GpuCompletion.completed();

        TransferBatch batch = TransferBatchManager.getOrCreate(device, queue);
        GpuCompletion completion = batch.recordMultiRegionFromIterator(
                mirror.mirrorHandle(), vkBuffer.handle(), cpuDirty.dirtyRegions(), regionCount);

        cpuDirty.clear();
        return completion;
    }

    /**
     * Marks a range as modified by the GPU (e.g. after a compute dispatch).
     * Call this after GPU work that wrote to this buffer has completed.
     */
    public void markGpuDirty(long offset, long size) {
        gpuDirty.markDirty(offset, size);
    }

    /**
     * Reads back GPU-dirty ranges into the mirror. Multi-region copy from primary -> mirror.
     * No-op if the GPU dirty strategy has no dirty ranges or observability is not mirrored.
     *
     * @param queue the queue to issue the copy on
     * @return a completion for the readback
     */
    public GpuCompletion readDiff(VkQueue queue) {
        if (!gpuDirty.isDirty()) return GpuCompletion.completed();
        if (!(observability instanceof MirrorCapable mirror)) return GpuCompletion.completed();

        int regionCount = gpuDirty.dirtyRegionCount();
        if (regionCount == 0) return GpuCompletion.completed();

        TransferBatch batch = TransferBatchManager.getOrCreate(device, queue);
        GpuCompletion completion = batch.recordMultiRegionFromIterator(
                vkBuffer.handle(), mirror.mirrorHandle(), gpuDirty.dirtyRegions(), regionCount);

        gpuDirty.clear();
        return completion;
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
        if (deferred && observability.isMirrored()) {
            // In deferred mode with a mirror: write to the mirror, mark dirty, no GPU transfer
            int length = data.remaining();
            MemorySegment mirrorSlice = observability.acquireReadable(offset, length);
            if (mirrorSlice != null) {
                MemorySegment.copy(MemorySegment.ofBuffer(data), 0, mirrorSlice, 0, length);
                cpuDirty.markDirty(offset, length);
                return GpuCompletion.completed();
            }
        }
        return transfer.writeAsync(context, data, offset, queue);
    }

    @Override
    public BufferWriteScope acquireWrite(long offset, long size, VkQueue queue) {
        if (deferred && observability instanceof MirrorCapable mirror) {
            MemorySegment target = mirror.mirrorMemory().asSlice(offset, size);
            return DeferredMirrorWriteScope.acquire(target, offset, size, cpuDirty);
        }
        return transfer.acquireWrite(context, offset, size, queue);
    }

    @Override
    public BufferReadScope acquireRead(long offset, long size, VkQueue queue) {
        // If observability provides readable memory, use it (zero-cost for mirrored/inherent)
        MemorySegment readable = observability.acquireReadable(offset, size);
        if (readable != null) {
            return ObservableReadScope.acquire(readable, offset, size);
        }
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
                observability.close();
                if (context.sparsePages != null) context.sparsePages.close();
                if (vkBuffer != null) vkBuffer.close();
            } finally {
                arena.close();
            }
        }
    }

    /**
     * Fluent builder for directly composing an {@link AllocationStrategy},
     * {@link TransferStrategy}, {@link CpuObservability}, and {@link DirtyStrategy}.
     * Prefer {@link BufferFactory} for the common cases —
     * this builder is for callers that need explicit control over the composition.
     */
    public static class Builder {
        private VkDevice device;
        private long size;
        private BufferUsage usage;
        private AllocationStrategy allocation;
        private TransferStrategy transfer;
        private CpuObservability observability;
        private DirtyStrategy cpuDirty;
        private DirtyStrategy gpuDirty;

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

        /** Sets the CPU observability strategy — how the CPU reads buffer contents. */
        public Builder observability(CpuObservability observability) {
            this.observability = observability;
            return this;
        }

        /** Sets the CPU->GPU dirty tracking strategy. */
        public Builder dirtyStrategy(DirtyStrategy cpuDirty) {
            this.cpuDirty = cpuDirty;
            return this;
        }

        /** Sets the GPU->CPU dirty tracking strategy. */
        public Builder gpuDirtyStrategy(DirtyStrategy gpuDirty) {
            this.gpuDirty = gpuDirty;
            return this;
        }

        public ManagedBuffer build() {
            if (device == null) throw new IllegalStateException("device not set");
            if (size <= 0) throw new IllegalStateException("invalid size");
            if (usage == null) throw new IllegalStateException("usage not set");
            if (allocation == null) throw new IllegalStateException("allocation strategy not set");
            if (transfer == null) throw new IllegalStateException("transfer strategy not set");

            // Infer observability if not set
            if (observability == null) {
                observability = NoneObservability.INSTANCE;
            }
            // Infer dirty strategies if not set
            if (cpuDirty == null) {
                cpuDirty = DirtyStrategy.forSize(size);
            }
            if (gpuDirty == null) {
                gpuDirty = DirtyStrategy.forSize(size);
            }

            return new ManagedBuffer(device, size, usage, allocation, transfer,
                    observability, cpuDirty, gpuDirty);
        }
    }
}
