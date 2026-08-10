package io.github.yetyman.vulkan.mesh.residency;

/**
 * Identifies one partition of one geometry: the unit that {@link ResidencyTracker} and
 * {@link EvictionPolicy} operate on. Residency is tracked per partition rather than per geometry so
 * that a coarse representation, a subset of visible terrain tiles, or a handful of nearby clusters
 * can be resident while the rest of the same geometry is not.
 *
 * @param geometry       the owning geometry's stable identity
 * @param partitionIndex the partition's index within that geometry's {@code PartitionSet}
 */
public record PartitionRef(GeometryId geometry, int partitionIndex) {
}
