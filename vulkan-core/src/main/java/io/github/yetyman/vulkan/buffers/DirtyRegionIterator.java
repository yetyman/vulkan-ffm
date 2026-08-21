package io.github.yetyman.vulkan.buffers;

/**
 * Zero-allocation iterator over dirty byte regions. Returned by {@link DirtyStrategy#dirtyRegions()}.
 *
 * <p>The caller must not retain this object past the next call to
 * {@link DirtyStrategy#clear()} or {@link DirtyStrategy#markDirty(long, long)} — implementations
 * may reuse the iterator instance internally.
 *
 * <p>Usage:
 * <pre>{@code
 * DirtyRegionIterator it = dirtyStrategy.dirtyRegions();
 * while (it.hasNext()) {
 *     it.next();
 *     long off = it.offset();
 *     long sz = it.size();
 *     // ... issue copy for [off, off + sz) ...
 * }
 * }</pre>
 */
public interface DirtyRegionIterator {

    /**
     * @return true if there is another dirty region to consume
     */
    boolean hasNext();

    /**
     * Advances to the next dirty region. Must only be called when {@link #hasNext()} is true.
     */
    void next();

    /**
     * @return the byte offset of the current dirty region (valid after {@link #next()})
     */
    long offset();

    /**
     * @return the byte size of the current dirty region (valid after {@link #next()})
     */
    long size();
}
