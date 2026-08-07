package io.github.yetyman.vulkan.mesh.residency;

import io.github.yetyman.vulkan.buffers.GpuCompletion;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Defers freeing geometry allocations until the GPU work that reads them has finished.
 *
 * <p>Freeing an allocation while a submitted command buffer still references it is a use-after-free
 * that typically manifests as corrupted geometry or a device loss several frames later. Reallocation
 * and topology replacement both hit this: the new data is uploaded and the old allocation is no
 * longer wanted, but frames already in flight are still drawing from it.
 *
 * <p>The pattern is amortized rather than per-allocation: entries carry the completion that must
 * finish first, and {@link #drain()} is called once per frame, reading each completion's status and
 * freeing everything that is ready. That costs one status check per pending entry per frame instead
 * of a blocking wait per free.
 *
 * <p>Not thread-safe; own one per thread that retires allocations, or guard it externally.
 */
public final class RetireQueue implements AutoCloseable {

    private record Entry(GeometryAllocator owner, GeometryAllocation allocation, GpuCompletion notBefore) {
    }

    private final Deque<Entry> pending = new ArrayDeque<>();

    /**
     * Queues an allocation to be freed once {@code notBefore} completes.
     *
     * @param owner     the allocator that produced the allocation, and will free it
     * @param allocation the allocation to release
     * @param notBefore  work that must finish before the release is safe; pass
     *                   {@link GpuCompletion#completed()} when nothing is in flight
     */
    public void retire(GeometryAllocator owner, GeometryAllocation allocation, GpuCompletion notBefore) {
        if (owner == null) throw new IllegalArgumentException("owner required");
        if (allocation == null) throw new IllegalArgumentException("allocation required");
        pending.add(new Entry(owner, allocation,
                notBefore != null ? notBefore : GpuCompletion.completed()));
    }

    /**
     * Frees every queued allocation whose guarding work has finished, without blocking.
     * Call once per frame.
     *
     * @return the number of allocations freed
     */
    public int drain() {
        int freed = 0;
        int remaining = pending.size();
        while (remaining-- > 0) {
            Entry e = pending.poll();
            if (e == null) break;
            if (e.notBefore().isComplete()) {
                e.owner().free(e.allocation());
                e.notBefore().close();
                freed++;
            } else {
                pending.add(e); // still in flight; re-check next drain
            }
        }
        return freed;
    }

    /**
     * @return the number of allocations still waiting to be freed
     */
    public int pendingCount() {
        return pending.size();
    }

    /**
     * Blocks until all queued work completes, then frees everything. For teardown.
     */
    public void drainBlocking() {
        Entry e;
        while ((e = pending.poll()) != null) {
            e.notBefore().await();
            e.owner().free(e.allocation());
            e.notBefore().close();
        }
    }

    @Override
    public void close() {
        drainBlocking();
    }
}
