package io.github.yetyman.vulkan.mesh.partition;

import io.github.yetyman.helpers.math.geometry.AABB;
import io.github.yetyman.helpers.math.spatial.SpatialStructure;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * An ordered collection of {@link GeometryPartition}s with optional spatial hierarchy and typed
 * per-partition metadata channels.
 *
 * <p>The spatial hierarchy is swappable per use case: a BVH suits general clustered geometry, a
 * quadtree suits terrain, a dense grid suits uniformly chunked voxel worlds, and no hierarchy at
 * all suits a mesh with four submeshes.
 */
public final class PartitionSet {

    private final List<GeometryPartition> partitions;
    private final AABB bounds;
    private final SpatialStructure<GeometryPartition> hierarchy;
    private PartitionMetadata metadata; // lazily initialized

    private PartitionSet(List<GeometryPartition> partitions, AABB bounds,
                         SpatialStructure<GeometryPartition> hierarchy) {
        this.partitions = partitions;
        this.bounds = bounds;
        this.hierarchy = hierarchy;
    }

    /**
     * Creates a partition set with no spatial hierarchy.
     */
    public static PartitionSet of(List<GeometryPartition> partitions, AABB bounds) {
        return new PartitionSet(List.copyOf(partitions), bounds, null);
    }

    /**
     * Creates a partition set with a spatial hierarchy for culling and LOD.
     */
    public static PartitionSet of(List<GeometryPartition> partitions, AABB bounds,
                                  SpatialStructure<GeometryPartition> hierarchy) {
        return new PartitionSet(List.copyOf(partitions), bounds, hierarchy);
    }

    /**
     * Creates a partition set with no spatial hierarchy, computing bounds from the partitions.
     */
    public static PartitionSet of(List<GeometryPartition> partitions) {
        if (partitions.isEmpty()) throw new IllegalArgumentException("partitions must not be empty");
        AABB combined = AABB.fromMinMax(partitions.getFirst().bounds().min, partitions.getFirst().bounds().max);
        for (int i = 1; i < partitions.size(); i++) {
            combined.merge(partitions.get(i).bounds());
        }
        return new PartitionSet(List.copyOf(partitions), combined, null);
    }

    /**
     * Creates a single-partition set covering the entire geometry.
     */
    public static PartitionSet single(GeometryPartition partition) {
        return new PartitionSet(List.of(partition), partition.bounds(), null);
    }

    public int count() {
        return partitions.size();
    }

    public GeometryPartition get(int index) {
        return partitions.get(index);
    }

    public List<GeometryPartition> partitions() {
        return Collections.unmodifiableList(partitions);
    }

    public AABB bounds() {
        return bounds;
    }

    /**
     * @return optional spatial hierarchy over the partitions. The structure implementation is the
     * caller's choice and this class is indifferent to which.
     */
    public Optional<SpatialStructure<GeometryPartition>> hierarchy() {
        return Optional.ofNullable(hierarchy);
    }

    /**
     * @return the per-partition metadata registry for this set. Lazily created on first access
     * so partition sets that never use metadata channels pay no cost.
     */
    public PartitionMetadata metadata() {
        if (metadata == null) {
            metadata = new PartitionMetadata(partitions.size());
        }
        return metadata;
    }

    /**
     * @return true if metadata has been accessed (and thus allocated) for this partition set
     */
    public boolean hasMetadata() {
        return metadata != null;
    }
}
