package io.github.yetyman.vulkan.mesh.partition;

import io.github.yetyman.vulkan.mesh.source.GeometrySource;

/**
 * Produces a {@link PartitionSet} from a {@link GeometrySource}. Different implementations
 * partition differently: use native source partitions, treat the whole mesh as one partition,
 * split by a per-primitive tag function, or build meshlets.
 */
public interface PartitioningStrategy {

    /**
     * @param source the geometry to partition
     * @return the resulting partition set
     */
    PartitionSet partition(GeometrySource source);
}
