package io.github.yetyman.vulkan.mesh.partition;

import io.github.yetyman.vulkan.mesh.source.GeometrySource;
import io.github.yetyman.vulkan.mesh.source.IndexStream;

/**
 * Uses whatever partitions the source declares natively (glTF primitives, OBJ groups, etc.).
 * The default strategy. Falls back to {@link SinglePartition} when the source declares no
 * partitions of its own.
 *
 * <p>Currently all built-in sources (procedural primitives, MeshOutputSource) have no native
 * partitions, so this behaves identically to {@link SinglePartition} for them. Once file-format
 * adapters are added, this will return the submeshes/primitives the format defines.
 */
public final class NativePartitioning implements PartitioningStrategy {

    /** Shared stateless instance. */
    public static final NativePartitioning INSTANCE = new NativePartitioning();

    private NativePartitioning() {}

    @Override
    public PartitionSet partition(GeometrySource source) {
        // Future: when GeometrySource gains a partitions() method returning native partitions,
        // use those here. For now, fall back to single partition.
        return SinglePartition.INSTANCE.partition(source);
    }
}
