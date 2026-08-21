package io.github.yetyman.vulkan.buffers;

/**
 * Tracks which byte ranges of a buffer have been modified since last flush.
 * Implementations coalesce adjacent/overlapping ranges to minimize transfer count.
 *
 * <p>Write-optimized: {@link #markDirty(long, long)} is expected to be in CPU hot paths and
 * must be appropriately fast. Region iteration ({@link #dirtyRegions()}) happens on the
 * rare/scheduled flush path.
 *
 * <p>Thread-safe: {@link #markDirty(long, long)} may be called concurrently from multiple
 * threads (e.g. parallel CPU work nodes writing disjoint regions of the same buffer).
 * Thread-safety is the implementation's responsibility.
 *
 * <p>Implementations may accept their backing collection through a non-default constructor or
 * factory for customization.
 */
public interface DirtyStrategy {

    /**
     * Marks a byte range as dirty. Thread-safe. Must be fast (hot path).
     *
     * @param offset start of the dirty range in bytes
     * @param size   length of the dirty range in bytes
     */
    void markDirty(long offset, long size);

    /**
     * @return number of coalesced dirty regions available
     */
    int dirtyRegionCount();

    /**
     * @return an iterator over the coalesced dirty regions. Caller must not retain past
     *         the next {@link #clear()} or {@link #markDirty(long, long)} call.
     */
    DirtyRegionIterator dirtyRegions();

    /**
     * Clears all dirty state. Called after a successful flush.
     */
    void clear();

    /**
     * @return true if any ranges are currently marked dirty
     */
    boolean isDirty();

    /**
     * Selects a dirty strategy based on buffer size.
     *
     * @param bufferSize the buffer size in bytes
     * @return an appropriate DirtyStrategy for the given size
     */
    static DirtyStrategy forSize(long bufferSize) {
        if (bufferSize < 4096) {
            return new AlwaysDirtyStrategy(bufferSize);
        } else if (bufferSize < 1024 * 1024) {
            return new RangeCoalescingDirtyStrategy();
        } else {
            return new BitSetDirtyStrategy(bufferSize);
        }
    }
}
