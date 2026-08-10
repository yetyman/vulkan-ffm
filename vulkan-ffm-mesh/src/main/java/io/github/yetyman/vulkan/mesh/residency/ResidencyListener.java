package io.github.yetyman.vulkan.mesh.residency;

import io.github.yetyman.vulkan.mesh.source.Residency;

/**
 * Notified when a tracked partition's residency state changes. This is the seam a
 * {@code GeometryTable} uses to keep its {@code FLAG_RESIDENT} bit and draw-eligibility in sync with
 * what is actually loaded, and the seam a future LOD selector uses to know which representations of
 * a geometry are currently available to draw.
 *
 * <p>Listeners are called synchronously from whatever thread drives {@link ResidencyTracker}
 * updates (typically the frame thread after polling completions). They must not block and must not
 * re-enter the tracker; do bookkeeping only, and defer anything expensive to the next frame.
 */
public interface ResidencyListener {

    /**
     * @param ref      which partition changed
     * @param oldState the state it was in before this transition
     * @param newState the state it is in now
     */
    void onResidencyChanged(PartitionRef ref, Residency oldState, Residency newState);
}
