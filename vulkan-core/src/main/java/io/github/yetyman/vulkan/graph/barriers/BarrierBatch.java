package io.github.yetyman.vulkan.graph.barriers;

import io.github.yetyman.vulkan.VkBarrier;
import io.github.yetyman.vulkan.command.VkBarrierCmd;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * Accumulates barriers for a single transition point between passes.
 * Each barrier retains its own src/dst stage masks so they can be emitted
 * with correct per-barrier granularity rather than a single combined mask.
 *
 * Supports two categories:
 * - Regular barriers: same-queue transitions, emitted on the consuming node's command buffer
 * - Ownership transfers: cross-queue transitions requiring a release+acquire pair on separate
 *   command buffers. These are accumulated separately and the executor routes them to the
 *   correct queue's command buffer.
 *
 * Uses fixed-capacity arrays to avoid per-frame ArrayList allocations.
 *
 * Batched execution: {@link #executeBatched(MemorySegment, Arena)} coalesces all barriers
 * into a single vkCmdPipelineBarrier call with combined stage masks and contiguous barrier
 * arrays. This is significantly faster than emitting one vkCmdPipelineBarrier per barrier.
 */
public class BarrierBatch {

    private static final int DEFAULT_CAPACITY = 16;
    private static final int DEFAULT_TRANSFER_CAPACITY = 4;

    private VkBarrier[] barriers;
    private int[] srcStages;
    private int[] dstStages;
    private int count;

    private OwnershipTransfer[] transfers;
    private int transferCount;

    public BarrierBatch() {
        this(DEFAULT_CAPACITY);
    }

    public BarrierBatch(int capacity) {
        this.barriers = new VkBarrier[capacity];
        this.srcStages = new int[capacity];
        this.dstStages = new int[capacity];
        this.count = 0;
        this.transfers = new OwnershipTransfer[DEFAULT_TRANSFER_CAPACITY];
        this.transferCount = 0;
    }

    /** Adds a same-queue barrier to this batch with its specific stage masks */
    public void add(VkBarrier barrier, int srcStage, int dstStage) {
        if (count == barriers.length) {
            int newCap = barriers.length * 2;
            VkBarrier[] grownBarriers = new VkBarrier[newCap];
            int[] grownSrc = new int[newCap];
            int[] grownDst = new int[newCap];
            System.arraycopy(barriers, 0, grownBarriers, 0, count);
            System.arraycopy(srcStages, 0, grownSrc, 0, count);
            System.arraycopy(dstStages, 0, grownDst, 0, count);
            barriers = grownBarriers;
            srcStages = grownSrc;
            dstStages = grownDst;
        }
        barriers[count] = barrier;
        srcStages[count] = srcStage;
        dstStages[count] = dstStage;
        count++;
    }

    /**
     * Adds a queue ownership transfer pair. The executor is responsible for recording
     * the release barrier on the source queue and the acquire barrier on the destination queue.
     */
    public void addOwnershipTransfer(OwnershipTransfer transfer) {
        if (transferCount == transfers.length) {
            OwnershipTransfer[] grown = new OwnershipTransfer[transfers.length * 2];
            System.arraycopy(transfers, 0, grown, 0, transferCount);
            transfers = grown;
        }
        transfers[transferCount++] = transfer;
    }

    /** @return barrier at index i */
    public VkBarrier get(int i) { return barriers[i]; }

    /** @return source stage mask for barrier at index i */
    public int srcStage(int i) { return srcStages[i]; }

    /** @return destination stage mask for barrier at index i */
    public int dstStage(int i) { return dstStages[i]; }

    /** @return number of same-queue barriers in this batch */
    public int count() { return count; }

    /** @return combined source stage mask for all same-queue barriers */
    public int combinedSrcStageMask() {
        int mask = 0;
        for (int i = 0; i < count; i++) mask |= srcStages[i];
        return mask;
    }

    /** @return combined destination stage mask for all same-queue barriers */
    public int combinedDstStageMask() {
        int mask = 0;
        for (int i = 0; i < count; i++) mask |= dstStages[i];
        return mask;
    }

    /** @return true if this batch has no barriers of any kind */
    public boolean isEmpty() { return count == 0 && transferCount == 0; }

    /** @return true if this batch has no same-queue barriers */
    public boolean hasNoSameQueueBarriers() { return count == 0; }

    /** @return ownership transfer at index i */
    public OwnershipTransfer getTransfer(int i) { return transfers[i]; }

    /** @return number of ownership transfers in this batch */
    public int transferCount() { return transferCount; }

    /** @return true if this batch has ownership transfers */
    public boolean hasOwnershipTransfers() { return transferCount > 0; }

    /**
     * Executes all same-queue barriers in a single vkCmdPipelineBarrier call by coalescing
     * barriers of the same type into contiguous arrays. This is the preferred execution path
     * as it minimizes driver overhead (one call instead of N).
     *
     * Barriers are grouped by type (memory, buffer, image) and emitted in one combined call
     * with the union of all src/dst stage masks.
     *
     * @param commandBuffer the command buffer handle
     * @param arena arena for temporary array allocation (frame arena)
     */
    public void executeBatched(MemorySegment commandBuffer, Arena arena) {
        if (count == 0) return;

        // Count barriers by type
        int memoryCount = 0, bufferCount = 0, imageCount = 0;
        for (int i = 0; i < count; i++) {
            switch (barriers[i].getType()) {
                case MEMORY -> memoryCount++;
                case BUFFER -> bufferCount++;
                case IMAGE -> imageCount++;
            }
        }

        // Build contiguous arrays for each type
        long bufBarrierSize = io.github.yetyman.vulkan.generated.VkBufferMemoryBarrier.layout().byteSize();
        long imgBarrierSize = io.github.yetyman.vulkan.generated.VkImageMemoryBarrier.layout().byteSize();
        long memBarrierSize = io.github.yetyman.vulkan.generated.VkMemoryBarrier.layout().byteSize();

        MemorySegment memBarriers = memoryCount > 0
            ? arena.allocate(io.github.yetyman.vulkan.generated.VkMemoryBarrier.layout(), memoryCount)
            : MemorySegment.NULL;
        MemorySegment bufBarriers = bufferCount > 0
            ? arena.allocate(io.github.yetyman.vulkan.generated.VkBufferMemoryBarrier.layout(), bufferCount)
            : MemorySegment.NULL;
        MemorySegment imgBarriers = imageCount > 0
            ? arena.allocate(io.github.yetyman.vulkan.generated.VkImageMemoryBarrier.layout(), imageCount)
            : MemorySegment.NULL;

        int memIdx = 0, bufIdx = 0, imgIdx = 0;
        for (int i = 0; i < count; i++) {
            VkBarrier barrier = barriers[i];
            MemorySegment src = barrier.handle();
            switch (barrier.getType()) {
                case MEMORY -> {
                    MemorySegment.copy(src, 0, memBarriers, memIdx * memBarrierSize, memBarrierSize);
                    memIdx++;
                }
                case BUFFER -> {
                    MemorySegment.copy(src, 0, bufBarriers, bufIdx * bufBarrierSize, bufBarrierSize);
                    bufIdx++;
                }
                case IMAGE -> {
                    MemorySegment.copy(src, 0, imgBarriers, imgIdx * imgBarrierSize, imgBarrierSize);
                    imgIdx++;
                }
            }
        }

        int combinedSrc = combinedSrcStageMask();
        int combinedDst = combinedDstStageMask();

        VkBarrierCmd.pipelineBarrier(commandBuffer, combinedSrc, combinedDst, 0,
            memoryCount, memBarriers, bufferCount, bufBarriers, imageCount, imgBarriers);
    }

    /** Resets the batch for reuse. Does not deallocate the backing arrays. */
    public void clear() {
        for (int i = 0; i < count; i++) {
            barriers[i] = null;
        }
        count = 0;

        for (int i = 0; i < transferCount; i++) {
            transfers[i] = null;
        }
        transferCount = 0;
    }
}
