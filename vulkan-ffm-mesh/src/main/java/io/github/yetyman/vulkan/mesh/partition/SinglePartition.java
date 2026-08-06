package io.github.yetyman.vulkan.mesh.partition;

import io.github.yetyman.vulkan.mesh.source.GeometrySource;
import io.github.yetyman.vulkan.mesh.source.IndexStream;

/**
 * Treats the entire geometry as a single partition. The simplest strategy: one partition covering
 * all vertices and indices with the source's topology and bounds.
 */
public final class SinglePartition implements PartitioningStrategy {

    /** Shared stateless instance. */
    public static final SinglePartition INSTANCE = new SinglePartition();

    private SinglePartition() {}

    @Override
    public PartitionSet partition(GeometrySource source) {
        long indexCount = source.indices().map(IndexStream::indexCount).orElse(0L);
        int ipp = source.topology().indicesPerPrimitive();
        long primitiveCount = ipp > 0 ? indexCount / ipp : source.elementCount();

        GeometryPartition single = new GeometryPartition(
                "", 0, primitiveCount, source.elementCount(),
                source.topology(), source.bounds(), 0, 0);
        return PartitionSet.single(single);
    }
}
