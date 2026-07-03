package io.github.yetyman.vulkan.buffers;

/**
 * Capability interface for buffers backed by sparse (virtual) Vulkan memory.
 * Implemented by {@link ManagedBuffer} instances configured with {@link MemoryStrategy#SPARSE}.
 * Consumers that need sparse-specific control (page commit/decommit for virtual texturing,
 * streaming, or similar demand-paged usage) should check {@code instanceof SparseCapable}
 * rather than assuming every {@link IBuffer} supports it.
 */
public interface SparseCapable {

    /**
     * @return the sparse page size in bytes for this buffer.
     */
    long pageSize();

    /**
     * Commits (binds physical memory to) all pages covering {@code [offset, offset + length)}.
     * Pages already committed are left untouched. Safe to call before every write.
     */
    void commitPages(long offset, long length);

    /**
     * Decommits (unbinds and returns to the free pool) all pages fully covered by
     * {@code [offset, offset + length)}. Partially-covered pages at the range boundaries
     * are left committed.
     */
    void decommitPages(long offset, long length);

    /**
     * @return true if every page covering {@code [offset, offset + length)} is currently committed.
     */
    boolean isCommitted(long offset, long length);
}
