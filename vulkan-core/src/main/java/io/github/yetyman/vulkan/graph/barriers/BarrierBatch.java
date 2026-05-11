package io.github.yetyman.vulkan.graph.barriers;

import io.github.yetyman.vulkan.VkBarrier;

/**
 * Accumulates barriers for a single transition point between passes.
 * Batched barriers are emitted as a single vkCmdPipelineBarrier call.
 *
 * Uses a fixed-capacity array to avoid per-frame ArrayList allocations.
 */
public class BarrierBatch {

    private static final int DEFAULT_CAPACITY = 16;

    private VkBarrier[] barriers;
    private int count;
    private int srcStageMask = 0;
    private int dstStageMask = 0;

    public BarrierBatch() {
        this(DEFAULT_CAPACITY);
    }

    public BarrierBatch(int capacity) {
        this.barriers = new VkBarrier[capacity];
        this.count = 0;
    }

    /** Adds a barrier to this batch */
    public void add(VkBarrier barrier, int srcStage, int dstStage) {
        if (count == barriers.length) {
            // Grow (rare -- only if a node has many resource transitions)
            VkBarrier[] grown = new VkBarrier[barriers.length * 2];
            System.arraycopy(barriers, 0, grown, 0, count);
            barriers = grown;
        }
        barriers[count++] = barrier;
        srcStageMask |= srcStage;
        dstStageMask |= dstStage;
    }

    /** @return barrier at index i */
    public VkBarrier get(int i) { return barriers[i]; }

    /** @return number of barriers in this batch */
    public int count() { return count; }

    /** @return combined source stage mask */
    public int srcStageMask() { return srcStageMask; }

    /** @return combined destination stage mask */
    public int dstStageMask() { return dstStageMask; }

    /** @return true if this batch has no barriers */
    public boolean isEmpty() { return count == 0; }

    /** Resets the batch for reuse. Does not deallocate the backing array. */
    public void clear() {
        // Null out references to allow GC of barrier structs (arena-managed, but be clean)
        for (int i = 0; i < count; i++) {
            barriers[i] = null;
        }
        count = 0;
        srcStageMask = 0;
        dstStageMask = 0;
    }

    /** @return all barriers as a list (for compatibility). Allocates -- avoid in hot path. */
    public java.util.List<VkBarrier> barriers() {
        return java.util.List.of(java.util.Arrays.copyOf(barriers, count));
    }
}
