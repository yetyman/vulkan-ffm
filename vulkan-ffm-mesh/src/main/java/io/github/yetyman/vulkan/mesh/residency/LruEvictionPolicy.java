package io.github.yetyman.vulkan.mesh.residency;

import io.github.yetyman.vulkan.mesh.source.Residency;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The default {@link EvictionPolicy}: selects the least-recently-touched partitions first, skipping
 * anything already evicting, not resident, or holding outstanding claims. Recency comes from
 * {@link ResidencyView#lastTouchedTick}, which {@link ResidencyTracker} advances on every
 * {@link ResidencyTracker#request}.
 *
 * <p>This has no notion of camera distance, screen-space size, or priority tier. Those are exactly
 * the reasons {@link EvictionPolicy} is an interface: a streaming terrain or open-world system
 * should replace this with something distance-aware, not extend it.
 */
public final class LruEvictionPolicy implements EvictionPolicy {

    @Override
    public List<PartitionRef> selectVictims(long bytesNeeded, ResidencyView view) {
        List<PartitionRef> candidates = new ArrayList<>();
        for (PartitionRef ref : view.tracked()) {
            Residency state = view.stateOf(ref);
            if (state != Residency.DEVICE && state != Residency.HOST_AND_DEVICE) continue;
            if (view.claimCountOf(ref) > 0) continue;
            candidates.add(ref);
        }
        candidates.sort(Comparator.comparingLong(view::lastTouchedTick));

        List<PartitionRef> victims = new ArrayList<>();
        long freed = 0;
        for (PartitionRef ref : candidates) {
            if (freed >= bytesNeeded) break;
            victims.add(ref);
            freed += view.byteSizeOf(ref);
        }
        return victims;
    }
}
