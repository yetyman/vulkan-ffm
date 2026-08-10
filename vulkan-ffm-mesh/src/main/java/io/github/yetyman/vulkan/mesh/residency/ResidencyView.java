package io.github.yetyman.vulkan.mesh.residency;

import io.github.yetyman.vulkan.mesh.source.Residency;

import java.util.List;

/**
 * A read-only view of currently-tracked partitions, given to an {@link EvictionPolicy} so it can
 * select victims without holding a reference to the tracker itself. Kept separate from
 * {@link ResidencyTracker} so a policy cannot accidentally mutate state it was only meant to read,
 * and so policies can be unit-tested against a hand-built view with no tracker involved at all.
 */
public interface ResidencyView {

    /**
     * @return every partition the tracker currently knows about, regardless of state. Includes
     * partitions mid-eviction ({@link Residency#EVICTING}) so a policy does not double-select them.
     */
    List<PartitionRef> tracked();

    /**
     * @return the partition's current residency state
     */
    Residency stateOf(PartitionRef ref);

    /**
     * @return the partition's approximate resident byte cost, or 0 if not resident. Used by policies
     * that select victims to satisfy a byte budget rather than a count.
     */
    long byteSizeOf(PartitionRef ref);

    /**
     * @return how many live callers currently hold a residency claim via
     * {@link ResidencyTracker#request}, not yet balanced by {@link ResidencyTracker#release}. A
     * policy should not select a partition with outstanding claims unless it has no other choice,
     * since evicting it would invalidate work a caller is actively depending on.
     */
    int claimCountOf(PartitionRef ref);

    /**
     * @return a monotonically increasing counter that advances once per {@link ResidencyTracker}
     * "touch" (request or an explicit recency hint). The default {@link LruEvictionPolicy} sorts by
     * this to find the least-recently-used partitions; a distance-aware or priority-aware policy is
     * free to ignore it entirely.
     */
    long lastTouchedTick(PartitionRef ref);
}
