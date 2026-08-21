package io.github.yetyman.vulkan.buffers;

import java.util.BitSet;

/**
 * Dirty strategy that divides the buffer into fixed-size pages and tracks dirtiness per page
 * using a {@link BitSet}. Best for large buffers (>= 1MB) with scattered small writes
 * (geometry tables, transform arrays, particle buffers).
 *
 * <p>{@link #markDirty(long, long)} is O(pages_touched) — sets one bit per page. Very fast
 * for typical writes that touch 1-4 pages.
 *
 * <p>{@link #dirtyRegions()} scans for contiguous runs of set bits, producing coalesced
 * regions aligned to page boundaries.
 *
 * <p>Thread-safe: operations synchronize on the internal BitSet.
 */
public final class BitSetDirtyStrategy implements DirtyStrategy {

    /** Default page granularity: 4KB */
    public static final int DEFAULT_PAGE_SIZE = 4096;

    private final long bufferSize;
    private final int pageSize;
    private final int pageCount;
    private final BitSet dirtyPages;
    private final ScanIterator iterator = new ScanIterator();

    /**
     * Creates a strategy with the default page size (4KB).
     *
     * @param bufferSize total buffer size in bytes
     */
    public BitSetDirtyStrategy(long bufferSize) {
        this(bufferSize, DEFAULT_PAGE_SIZE);
    }

    /**
     * Creates a strategy with a custom page size.
     *
     * @param bufferSize total buffer size in bytes
     * @param pageSize   granularity in bytes (each bit in the BitSet represents one page)
     */
    public BitSetDirtyStrategy(long bufferSize, int pageSize) {
        if (bufferSize <= 0) throw new IllegalArgumentException("bufferSize must be > 0");
        if (pageSize <= 0) throw new IllegalArgumentException("pageSize must be > 0");
        this.bufferSize = bufferSize;
        this.pageSize = pageSize;
        this.pageCount = (int) ((bufferSize + pageSize - 1) / pageSize);
        this.dirtyPages = new BitSet(pageCount);
    }

    @Override
    public void markDirty(long offset, long size) {
        if (size <= 0) return;
        int firstPage = (int) (offset / pageSize);
        int lastPage = (int) ((offset + size - 1) / pageSize);
        // Clamp to valid page range
        if (firstPage < 0) firstPage = 0;
        if (lastPage >= pageCount) lastPage = pageCount - 1;

        synchronized (dirtyPages) {
            dirtyPages.set(firstPage, lastPage + 1);
        }
    }

    @Override
    public int dirtyRegionCount() {
        synchronized (dirtyPages) {
            int count = 0;
            int i = dirtyPages.nextSetBit(0);
            while (i >= 0) {
                int end = dirtyPages.nextClearBit(i);
                count++;
                i = dirtyPages.nextSetBit(end);
            }
            return count;
        }
    }

    @Override
    public DirtyRegionIterator dirtyRegions() {
        synchronized (dirtyPages) {
            iterator.reset();
        }
        return iterator;
    }

    @Override
    public void clear() {
        synchronized (dirtyPages) {
            dirtyPages.clear();
        }
    }

    @Override
    public boolean isDirty() {
        synchronized (dirtyPages) {
            return !dirtyPages.isEmpty();
        }
    }

    /**
     * Reusable iterator that scans contiguous bit runs. Zero allocation per flush.
     */
    private final class ScanIterator implements DirtyRegionIterator {
        private int nextBit;
        private long currentOffset;
        private long currentSize;

        void reset() {
            this.nextBit = dirtyPages.nextSetBit(0);
            this.currentOffset = 0;
            this.currentSize = 0;
        }

        @Override
        public boolean hasNext() {
            return nextBit >= 0;
        }

        @Override
        public void next() {
            int runStart = nextBit;
            int runEnd = dirtyPages.nextClearBit(runStart);

            currentOffset = (long) runStart * pageSize;
            long rawEnd = (long) runEnd * pageSize;
            // Clamp to buffer size (last page may extend past buffer end)
            if (rawEnd > bufferSize) rawEnd = bufferSize;
            currentSize = rawEnd - currentOffset;

            nextBit = dirtyPages.nextSetBit(runEnd);
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
