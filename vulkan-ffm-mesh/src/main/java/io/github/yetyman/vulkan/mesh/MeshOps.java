package io.github.yetyman.vulkan.mesh;

import io.github.yetyman.vulkan.VkQueue;
import io.github.yetyman.vulkan.buffers.GpuCompletion;
import io.github.yetyman.vulkan.mesh.consume.GeometryBinding;
import io.github.yetyman.vulkan.mesh.residency.GeometryAllocation;
import io.github.yetyman.vulkan.mesh.residency.GeometryAllocator;
import io.github.yetyman.vulkan.mesh.residency.RetireQueue;
import io.github.yetyman.vulkan.mesh.residency.TransferBatchExecutor;
import io.github.yetyman.vulkan.mesh.residency.UploadExecutor;
import io.github.yetyman.vulkan.mesh.residency.UploadOp;
import io.github.yetyman.vulkan.mesh.residency.UploadPlan;
import io.github.yetyman.vulkan.mesh.residency.UploadPlanner;
import io.github.yetyman.vulkan.mesh.source.GeometrySource;

import java.util.ArrayList;
import java.util.List;

/**
 * Stateless operations on meshes that involve allocation lifecycle: reallocation when capacity
 * is exceeded, and swap-with-retire for topology replacement.
 *
 * <p>These are not methods on {@link Mesh} because {@code Mesh} is deliberately thin and carries
 * no behavior beyond wiring. These operations compose allocator, upload, and retire-queue concerns
 * that live below the aggregate. Convenience delegation methods on {@code Mesh} call through here.
 */
public final class MeshOps {

    private MeshOps() {}

    /**
     * Reallocates a mesh to a larger capacity, copies existing device-side data into the new
     * allocation via device-to-device copy, then retires the old allocation so it is freed once
     * the GPU finishes reading it.
     *
     * <p>The mesh's allocation, binding, and capacity fields are updated in place. The live
     * counts are preserved. The source is preserved so subsequent {@link Mesh#update} calls
     * still work.
     *
     * <p>Returns a {@link GpuCompletion} for the copy. The old allocation is scheduled for
     * release once that completion finishes (via the retire queue). Callers that need the new
     * data to be usable before recording draws should await or sequence on this completion.
     *
     * @param mesh              the mesh to reallocate
     * @param newVertexCapacity new vertex capacity (must be >= current live count)
     * @param newIndexCapacity  new index capacity (must be >= current live count)
     * @param allocator         the allocator to use for the new allocation
     * @param executor          upload executor for the device-to-device copy
     * @param queue             the queue to submit work on
     * @param retireQueue       retire queue for deferred freeing of the old allocation
     * @return completion for the copy operation
     * @throws IllegalArgumentException if new capacities are smaller than current live counts
     * @throws IllegalStateException if the mesh does not own its allocation
     */
    public static GpuCompletion reallocate(Mesh mesh, long newVertexCapacity, long newIndexCapacity,
                                           GeometryAllocator allocator, UploadExecutor executor,
                                           VkQueue queue, RetireQueue retireQueue) {
        if (!mesh.ownsAllocation())
            throw new IllegalStateException("cannot reallocate a mesh that does not own its allocation");
        if (newVertexCapacity < mesh.vertexCount())
            throw new IllegalArgumentException("newVertexCapacity " + newVertexCapacity
                    + " < current vertexCount " + mesh.vertexCount());
        if (newIndexCapacity < mesh.indexCount())
            throw new IllegalArgumentException("newIndexCapacity " + newIndexCapacity
                    + " < current indexCount " + mesh.indexCount());

        GeometryAllocation oldAlloc = mesh.allocation();
        MeshLayout layout = mesh.layout();

        // Allocate new space
        GeometryAllocation newAlloc = allocator.allocate(layout, newVertexCapacity,
                mesh.indexWidth(), newIndexCapacity);

        // Build device-to-device copy plan for existing data
        List<UploadOp> ops = new ArrayList<>();
        for (int s = 0; s < layout.streamCount(); s++) {
            long liveBytes = layout.strideOf(s) * mesh.vertexCount();
            if (liveBytes > 0) {
                ops.add(new UploadOp.DeviceCopy(
                        oldAlloc.vertexRange(s).buffer(), oldAlloc.vertexRange(s).offset(),
                        newAlloc.vertexRange(s).buffer(), newAlloc.vertexRange(s).offset(),
                        liveBytes));
            }
        }
        if (mesh.indexWidth() != null && mesh.indexCount() > 0 && oldAlloc.indexRange().isPresent()) {
            long liveIdxBytes = (long) mesh.indexWidth().byteSize() * mesh.indexCount();
            ops.add(new UploadOp.DeviceCopy(
                    oldAlloc.indexRange().get().buffer(), oldAlloc.indexRange().get().offset(),
                    newAlloc.indexRange().get().buffer(), newAlloc.indexRange().get().offset(),
                    liveIdxBytes));
        }

        UploadPlan plan = new UploadPlan(ops,
                0x00000004 | 0x00000002, // VERTEX_ATTRIBUTE_READ | INDEX_READ
                0x00000004,              // VERTEX_INPUT
                io.github.yetyman.vulkan.mesh.residency.QueueClass.TRANSFER,
                io.github.yetyman.vulkan.mesh.residency.Priority.NORMAL,
                false);

        GpuCompletion completion = executor.execute(plan, queue);

        // Retire the old allocation - it will be freed once the in-flight frames finish
        retireQueue.retire(allocator, oldAlloc, completion);

        // Update the mesh in place with the new allocation and capacity
        mesh.replaceAllocation(newAlloc, newVertexCapacity, newIndexCapacity);

        return completion;
    }

    /**
     * Swaps a mesh's allocation and binding with those from a freshly-built replacement, retiring
     * the old allocation so frames still in flight can finish reading it.
     *
     * <p>Use this for topology replacement (remesh, LOD swap): build a new {@link Mesh} from a new
     * source, then call this to atomically install it into the existing reference. The replacement
     * mesh is consumed (its allocation is adopted) and should not be closed separately.
     *
     * @param target      the mesh to update
     * @param replacement the newly-built mesh whose allocation replaces the target's
     * @param allocator   the allocator that owns the target's current allocation (for retire)
     * @param retireQueue retire queue for deferred free of the old allocation
     * @param notBefore   completion that must finish before the old allocation can be freed
     *                    (typically the last frame's render completion)
     */
    public static void swap(Mesh target, Mesh replacement, GeometryAllocator allocator,
                            RetireQueue retireQueue, GpuCompletion notBefore) {
        if (!target.ownsAllocation())
            throw new IllegalStateException("cannot swap a mesh that does not own its allocation");

        GeometryAllocation oldAlloc = target.allocation();

        // Retire the old allocation
        retireQueue.retire(allocator, oldAlloc, notBefore);

        // Install the replacement's state into the target
        target.replaceFrom(replacement);
    }
}
