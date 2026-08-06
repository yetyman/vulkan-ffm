package io.github.yetyman.vulkan.mesh.residency;

import io.github.yetyman.vulkan.VkQueue;
import io.github.yetyman.vulkan.buffers.GpuCompletion;

/**
 * Executes an {@link UploadPlan}, moving data from host to device.
 *
 * <p>Two implementations exist:
 * <ul>
 *   <li>{@link TransferBatchExecutor} — ships in this module. Acquires write scopes, transcodes
 *       directly into them, closes them. Needs no render graph.</li>
 *   <li>A graph-recording executor — lives app-side. Translates ops into transfer nodes
 *       and returns a timeline-semaphore-backed {@link GpuCompletion}.</li>
 * </ul>
 *
 * <p>Both consume the same {@link UploadPlan}, so the mesh module never learns which is in use.
 */
public interface UploadExecutor {

    /**
     * Executes the plan, returning a completion that resolves when all data is usable on the GPU.
     *
     * @param plan  the upload plan to execute
     * @param queue the queue to submit work to (or the queue whose batch to record into)
     * @return a completion for the uploaded data
     */
    GpuCompletion execute(UploadPlan plan, VkQueue queue);
}
