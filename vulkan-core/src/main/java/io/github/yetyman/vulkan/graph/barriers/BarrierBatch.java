package io.github.yetyman.vulkan.graph.barriers;

import io.github.yetyman.vulkan.VkBarrier;

/**
 * Accumulates barriers for a single transition point between passes.
 * Batched barriers are emitted as a single vkCmdPipelineBarrier call.
 *
 * Supports two categories:
 * - Regular barriers: same-queue transitions, emitted on the consuming node's command buffer
 * - Ownership transfers: cross-queue transitions requiring a release+acquire pair on separate
 *   command buffers. These are accumulated separately and the executor routes them to the
 *   correct queue's command buffer.
 *
 * Uses fixed-capacity arrays to avoid per-frame ArrayList allocations.
 */
public class BarrierBatch {

    private static final int DEFAULT_CAPACITY = 16;
    private static final int DEFAULT_TRANSFER_CAPACITY = 4;

    private VkBarrier[] barriers;
    private int count;
    private int srcStageMask = 0;
    private int dstStageMask = 0;

    private OwnershipTransfer[] transfers;
    private int transferCount;

    public BarrierBatch() {
        this(DEFAULT_CAPACITY);
    }

    public BarrierBatch(int capacity) {
        this.barriers = new VkBarrier[capacity];
        this.count = 0;
        this.transfers = new OwnershipTransfer[DEFAULT_TRANSFER_CAPACITY];
        this.transferCount = 0;
    }

    /** Adds a same-queue barrier to this batch */
    public void add(VkBarrier barrier, int srcStage, int dstStage) {
        if (count == barriers.length) {
            VkBarrier[] grown = new VkBarrier[barriers.length * 2];
            System.arraycopy(barriers, 0, grown, 0, count);
            barriers = grown;
        }
        barriers[count++] = barrier;
        srcStageMask |= srcStage;
        dstStageMask |= dstStage;
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

    /** @return number of same-queue barriers in this batch */
    public int count() { return count; }

    /** @return combined source stage mask for same-queue barriers */
    public int srcStageMask() { return srcStageMask; }

    /** @return combined destination stage mask for same-queue barriers */
    public int dstStageMask() { return dstStageMask; }

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

    /** Resets the batch for reuse. Does not deallocate the backing arrays. */
    public void clear() {
        for (int i = 0; i < count; i++) {
            barriers[i] = null;
        }
        count = 0;
        srcStageMask = 0;
        dstStageMask = 0;

        for (int i = 0; i < transferCount; i++) {
            transfers[i] = null;
        }
        transferCount = 0;
    }

    /** @return all same-queue barriers as a list (for compatibility). Allocates -- avoid in hot path. */
    public java.util.List<VkBarrier> barriers() {
        return java.util.List.of(java.util.Arrays.copyOf(barriers, count));
    }
}
