package io.github.yetyman.vulkan.buffers;

import java.util.ArrayList;
import java.util.List;

/**
 * Dirty strategy that maintains a sorted list of dirty byte intervals and merges
 * overlapping/adjacent intervals on each {@link #markDirty(long, long)} call.
 *
 * <p>Primary implementation for medium-sized buffers (4KB - 1MB). Coalesces dirty regions
 * using a configurable gap threshold: if two regions are separated by fewer than
 * {@code gapThreshold} bytes, they are merged into one (transferring the clean gap redundantly
 * to reduce region count and copy-command overhead).
 *
 * <p>Thread-safe: {@link #markDirty(long, long)} synchronizes on the internal list.
 * Contention is expected to be low — writes are fast, flush (iteration) is infrequent.
 */
public final class RangeCoalescingDirtyStrategy implements DirtyStrategy {

    /** Default gap threshold: regions within 256 bytes of each other are merged. */
    public static final long DEFAULT_GAP_THRESHOLD = 256;

    private final long gapThreshold;
    private final List<long[]> regions; // each entry is [offset, end)
    private final ListRegionIterator iterator = new ListRegionIterator();

    /**
     * Creates a strategy with the default gap threshold (256 bytes) and an ArrayList backing.
     */
    public RangeCoalescingDirtyStrategy() {
        this(DEFAULT_GAP_THRESHOLD, new ArrayList<>());
    }

    /**
     * Creates a strategy with a custom gap threshold.
     *
     * @param gapThreshold if two dirty regions are separated by fewer than this many bytes,
     *                     they are merged into one. Set to 0 for no gap merging.
     */
    public RangeCoalescingDirtyStrategy(long gapThreshold) {
        this(gapThreshold, new ArrayList<>());
    }

    /**
     * Creates a strategy with a custom gap threshold and backing collection.
     *
     * @param gapThreshold gap threshold in bytes
     * @param backingList  the list to use for interval storage (allows pre-sized or custom impls)
     */
    public RangeCoalescingDirtyStrategy(long gapThreshold, List<long[]> backingList) {
        this.gapThreshold = gapThreshold;
        this.regions = backingList;
    }

    @Override
    public void markDirty(long offset, long size) {
        if (size <= 0) return;
        long newStart = offset;
        long newEnd = offset + size;

        synchronized (regions) {
            // Find insertion point and merge with any overlapping/adjacent regions.
            // Regions are maintained sorted by start offset with no overlaps.
            int i = 0;
            while (i < regions.size()) {
                long[] existing = regions.get(i);
                long existStart = existing[0];
                long existEnd = existing[1];

                // If the new range (expanded by gap threshold) overlaps this existing range, merge
                if (newStart <= existEnd + gapThreshold && newEnd + gapThreshold >= existStart) {
                    newStart = Math.min(newStart, existStart);
                    newEnd = Math.max(newEnd, existEnd);
                    regions.remove(i);
                    // Don't increment i — check the next element at the same index
                } else if (existStart > newEnd + gapThreshold) {
                    // Past where we could merge — insert here
                    break;
                } else {
                    i++;
                }
            }
            regions.add(i, new long[]{newStart, newEnd});
        }
    }

    @Override
    public int dirtyRegionCount() {
        synchronized (regions) {
            return regions.size();
        }
    }

    @Override
    public DirtyRegionIterator dirtyRegions() {
        // Snapshot: the caller should not call markDirty during iteration.
        // Flush is a serialization point anyway.
        synchronized (regions) {
            iterator.reset(regions);
        }
        return iterator;
    }

    @Override
    public void clear() {
        synchronized (regions) {
            regions.clear();
        }
    }

    @Override
    public boolean isDirty() {
        synchronized (regions) {
            return !regions.isEmpty();
        }
    }

    /**
     * Reusable iterator over the region list. Zero allocation per flush.
     */
    private static final class ListRegionIterator implements DirtyRegionIterator {
        private List<long[]> source;
        private int index;
        private long currentOffset;
        private long currentSize;

        void reset(List<long[]> regions) {
            this.source = regions;
            this.index = -1;
            this.currentOffset = 0;
            this.currentSize = 0;
        }

        @Override
        public boolean hasNext() {
            return index + 1 < source.size();
        }

        @Override
        public void next() {
            index++;
            long[] region = source.get(index);
            currentOffset = region[0];
            currentSize = region[1] - region[0];
        }

        @Override
        public long offset() {
            return currentOffset;
        }

        @Override
        public long size() {
            return currentSize;
        }
    }
}
