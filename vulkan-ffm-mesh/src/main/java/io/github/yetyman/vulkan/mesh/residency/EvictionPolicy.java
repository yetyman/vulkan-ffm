package io.github.yetyman.vulkan.mesh.residency;

import java.util.List;

/**
 * Decides which resident partitions to evict when memory pressure requires reclaiming space.
 * Deliberately an interface with a default ({@link LruEvictionPolicy}), never a hardcoded rule: a
 * streaming terrain system wants distance-from-camera, an asset browser wants strict LRU, a
 * priority-tiered system wants to protect {@code IMMEDIATE}-priority partitions from ever being
 * selected. All three are legitimate and none of them belongs baked into {@link ResidencyTracker}.
 *
 * <p>Selection is advisory: the tracker decides whether and when to actually act on the returned
 * list (e.g. it will never evict a partition with outstanding claims regardless of what a policy
 * returns), so a policy is free to be approximate.
 */
public interface EvictionPolicy {

    /**
     * Selects partitions to evict to free at least {@code bytesNeeded}. Implementations should stop
     * selecting once the running total of {@link ResidencyView#byteSizeOf} across selected
     * partitions meets or exceeds the requested amount, rather than always draining every eligible
     * partition.
     *
     * @param bytesNeeded minimum bytes the caller wants freed
     * @param view        read-only snapshot of tracked partitions to select from
     * @return partitions to evict, in the order eviction should be attempted. May return fewer
     * bytes than requested if nothing else is eligible (e.g. everything remaining is claimed).
     */
    List<PartitionRef> selectVictims(long bytesNeeded, ResidencyView view);
}
