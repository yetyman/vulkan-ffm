package io.github.yetyman.vulkan.mesh.residency;

import io.github.yetyman.vulkan.buffers.GpuCompletion;
import io.github.yetyman.vulkan.mesh.source.Residency;

/**
 * Tracks and drives residency at partition granularity: what is currently loaded, what has been
 * requested but is not yet usable, and what should be evicted under memory pressure.
 *
 * <p>Granularity is deliberately per partition, not per geometry, because per-geometry residency
 * cannot express any of the things streaming exists for: a coarse representation loaded first while
 * detail streams in, only the terrain tiles currently in view, only the clusters within some
 * distance of the camera. See {@code plans/mesh/04-residency-and-upload.md}.
 *
 * <p>This interface says nothing about how loading happens. A default implementation
 * ({@link DefaultResidencyTracker}) drives it through a {@link GeometryAllocator} and
 * {@code UploadExecutor}, exactly like the rest of Layer 3, but nothing above this interface may
 * assume that particular wiring.
 */
public interface ResidencyTracker extends AutoCloseable {

    /**
     * @return the partition's current residency state, or {@link Residency#ABSENT} if it has never
     * been requested (equivalent to not being tracked at all)
     */
    Residency stateOf(PartitionRef ref);

    /**
     * Requests residency for a partition, loading it if it is not already resident or pending.
     * Increments the partition's outstanding claim count; the caller must eventually call
     * {@link #release} exactly once per call to this method.
     *
     * <p>Calling this on an already-resident partition still counts as a claim and still updates
     * its recency for LRU purposes, but returns an already-complete token rather than doing work.
     *
     * @param ref      the partition to make resident
     * @param priority scheduling hint for the underlying upload, if one is required
     * @return a completion that finishes once the partition is usable. Already complete if the
     * partition was already resident.
     */
    GpuCompletion request(PartitionRef ref, Priority priority);

    /**
     * Releases one residency claim previously acquired via {@link #request}. Does not evict
     * immediately; eviction is the {@link EvictionPolicy}'s decision, driven by
     * {@link #evict(long)}. A partition with zero outstanding claims is simply eligible for
     * eviction, not scheduled for it.
     *
     * @throws IllegalStateException if the partition has no outstanding claim to release
     */
    void release(PartitionRef ref);

    /**
     * Runs the configured {@link EvictionPolicy} to free at least {@code bytesNeeded}, evicting
     * whichever eligible partitions it selects. Partitions with outstanding claims are never
     * evicted, regardless of what the policy returns.
     *
     * @return the number of bytes actually freed, which may be less than requested if nothing
     * else was eligible
     */
    long evict(long bytesNeeded);

    /**
     * Registers a listener notified on every residency state transition. See
     * {@link ResidencyListener} for threading and re-entrancy constraints.
     */
    void addListener(ResidencyListener listener);

    /**
     * Unregisters a previously added listener. No-op if not registered.
     */
    void removeListener(ResidencyListener listener);

    @Override
    void close();
}
