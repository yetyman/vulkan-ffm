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
}
