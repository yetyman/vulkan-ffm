package io.github.yetyman.vulkan.mesh.lod;

import io.github.yetyman.vulkan.mesh.residency.PartitionRef;
import io.github.yetyman.vulkan.mesh.source.Residency;

/**
 * A non-blocking, non-allocating residency query function. Injected into {@link LodContext}
 * so selectors can check whether a partition is resident without depending on or holding a
 * reference to the full {@link io.github.yetyman.vulkan.mesh.residency.ResidencyTracker}.
 *
 * <p>Typical implementation: {@code tracker::stateOf} (method reference on ResidencyTracker).
 *
 * <p>This is a functional interface so it can be supplied as a lambda, method reference, or
 * a lookup table. The decoupling ensures selectors are unit-testable against a hand-built
 * residency function with no tracker involved.
 */
@FunctionalInterface
public interface ResidencyQuery {

    /**
     * @return the current residency state of the partition, or {@link Residency#DEVICE} if
     * residency tracking is not in use (the "everything is resident" default)
     */
    Residency query(PartitionRef ref);

    /**
     * A query that always returns {@link Residency#DEVICE}. Used when all geometry is
     * permanently resident and residency tracking is not needed.
     */
    ResidencyQuery ALL_RESIDENT = ref -> Residency.DEVICE;
}
