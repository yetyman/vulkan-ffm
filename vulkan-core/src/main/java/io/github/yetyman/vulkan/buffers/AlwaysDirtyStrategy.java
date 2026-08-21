package io.github.yetyman.vulkan.buffers;

/**
 * A dirty strategy that always reports the entire buffer as dirty. Useful for small buffers
 * (typically < 4KB, e.g. UBOs) where the overhead of tracking individual ranges exceeds
 * the savings from partial transfer.
 *
 * <p>{@link #markDirty(long, long)} is a no-op — everything is always dirty.
 * {@link #dirtyRegions()} always yields a single region covering the full buffer size.
 *
 * <p>Thread-safe trivially (all methods are stateless or read-only).
 */
public final class AlwaysDirtyStrategy implements DirtyStrategy {

    private final long bufferSize;
    private final SingleRegionIterator iterator;

    /**
     * @param bufferSize the full size of the buffer in bytes. The single dirty region
     *                   returned by {@link #dirtyRegions()} spans [0, bufferSize).
     */
    public AlwaysDirtyStrategy(long bufferSize) {
        this.bufferSize = bufferSize;
        this.iterator = new SingleRegionIterator();
    }

    @Override
    public void markDirty(long offset, long size) {
        // no-op: everything is always dirty
    }

    @Override
    public int dirtyRegionCount() {
        return 1;
    }

    @Override
    public DirtyRegionIterator dirtyRegions() {
        iterator.reset(0, bufferSize);
        return iterator;
    }

    @Override
    public void clear() {
        // no-op: always dirty regardless
    }

    @Override
    public boolean isDirty() {
        return true;
    }

    /**
     * Reusable single-element iterator. Zero allocation per flush.
     */
    private static final class SingleRegionIterator implements DirtyRegionIterator {
        private long offset;
        private long size;
        private boolean consumed;

        void reset(long offset, long size) {
            this.offset = offset;
            this.size = size;
            this.consumed = false;
        }

        @Override
        public boolean hasNext() {
            return !consumed;
        }

        @Override
        public void next() {
            consumed = true;
        }

        @Override
        public long offset() {
            return offset;
        }

        @Override
        public long size() {
            return size;
        }
    }
}
