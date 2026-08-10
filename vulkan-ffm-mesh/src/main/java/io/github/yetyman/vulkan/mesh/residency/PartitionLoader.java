package io.github.yetyman.vulkan.mesh.residency;

import io.github.yetyman.vulkan.VkQueue;
import io.github.yetyman.vulkan.buffers.GpuCompletion;

/**
 * Supplies the load/free behavior a {@link DefaultResidencyTracker} drives, so the tracker itself
 * stays ignorant of where a partition's {@link GeometrySource}, target layout, or allocator come
 * from. The tracker only knows partition identity, byte size, and how to ask this seam to make one
 * resident or drop one.
 *
 * <p>Typical implementations resolve {@code ref.geometry()} through an app-side registry to find
 * the right {@code GeometrySource}/{@code PartitionSet}, build an {@link UploadPlan} via
 * {@link UploadPlanner}, allocate through whichever {@link GeometryAllocator} that geometry uses,
 * and run the plan through an {@link UploadExecutor}. None of that is prescribed here because it
 * varies by app (a terrain streamer resolves tiles from disk; a meshlet system resolves clusters
 * already resident in a coarser LOD's allocation).
 */
public interface PartitionLoader {

    /**
     * Begins loading a partition, returning a completion that resolves once it is usable and an
     * approximate resident byte size for eviction accounting. The tracker calls this at most once
     * per partition while it is not resident; concurrent {@link ResidencyTracker#request} calls for
     * an already-pending partition share the same in-flight result rather than triggering a second
     * load.
     *
     * @param ref   the partition to load
     * @param queue the queue to submit any upload work to
     * @return the load's completion and its resident byte size
     */
    LoadResult load(PartitionRef ref, VkQueue queue);

    /**
     * Frees a previously loaded partition's allocation. Called only for partitions with zero
     * outstanding claims, after eviction has been decided; never called concurrently with an
     * in-flight {@link #load} for the same partition.
     */
    void unload(PartitionRef ref);

    /**
     * @param completion completion that resolves once the partition's data is usable
     * @param byteSize   approximate resident bytes, used by {@link EvictionPolicy} byte accounting
     */
    record LoadResult(GpuCompletion completion, long byteSize) {
    }
}
