package io.github.yetyman.vulkan.mesh.process;

import io.github.yetyman.vulkan.mesh.partition.PartitionSet;
import io.github.yetyman.vulkan.mesh.partition.PartitioningStrategy;
import io.github.yetyman.vulkan.mesh.source.GeometrySource;

/**
 * Interface for optimized meshlet building algorithms that partition geometry into small,
 * GPU-friendly clusters maximizing vertex reuse and minimizing overdraw.
 *
 * <p>This interface lives in {@code vulkan-ffm-mesh} so that any module can depend on and
 * implement it. It extends {@link PartitioningStrategy} since meshlet building is fundamentally
 * a partitioning operation. Optimized implementations (AMD/NV-style vertex cache optimization,
 * spatial sorting, cone culling metadata generation) belong in the
 * {@code vulkan-ffm-mesh-processing} sibling module.</p>
 *
 * <p>A naive reference implementation ({@link io.github.yetyman.vulkan.mesh.partition.ReferenceMeshletBuilder})
 * exists in the partition package for testing. It makes no optimization effort and should never
 * be used in production.</p>
 *
 * <p>Quality criteria for meshlet builders (in priority order):</p>
 * <ol>
 *   <li>Maximize vertex reuse within each meshlet (fewer unique vertices = less redundant
 *       vertex shader invocations)</li>
 *   <li>Minimize spatial extent of each meshlet (tighter bounds = better cone culling, better
 *       Hi-Z/early-Z)</li>
 *   <li>Produce meshlets of uniform size (avoid undersized tail meshlets that waste wave
 *       occupancy)</li>
 * </ol>
 */
public interface MeshletBuilder extends PartitioningStrategy {

    /**
     * Builds meshlets from the given geometry source.
     *
     * <p>Equivalent to {@link #partition(GeometrySource)} but named for clarity when the caller
     * specifically wants meshlets rather than generic partitioning.</p>
     *
     * @param source the geometry to partition into meshlets
     * @return a partition set where each partition is one meshlet
     */
    default PartitionSet buildMeshlets(GeometrySource source) {
        return partition(source);
    }

    /**
     * @return the maximum vertices per meshlet this builder is configured to produce
     */
    int maxVerticesPerMeshlet();

    /**
     * @return the maximum primitives per meshlet this builder is configured to produce
     */
    int maxPrimitivesPerMeshlet();
}
