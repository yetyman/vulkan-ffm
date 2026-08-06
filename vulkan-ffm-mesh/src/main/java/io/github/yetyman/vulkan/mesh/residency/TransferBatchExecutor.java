package io.github.yetyman.vulkan.mesh.residency;

import io.github.yetyman.vulkan.VkQueue;
import io.github.yetyman.vulkan.buffers.BufferWriteScope;
import io.github.yetyman.vulkan.buffers.GpuCompletion;
import io.github.yetyman.vulkan.buffers.TransferBatchManager;

import java.lang.foreign.MemorySegment;

/**
 * Executes an {@link UploadPlan} using the per-thread {@link TransferBatchManager} infrastructure.
 *
 * <p>For each op:
 * <ul>
 *   <li>{@link UploadOp.HostCopy}: acquires a write scope on the destination, copies from the host
 *       segment, closes the scope.</li>
 *   <li>{@link UploadOp.DeviceCopy}: records a device-to-device copy into the batch.</li>
 *   <li>{@link UploadOp.Transcode}: acquires a write scope, calls the stream's
 *       {@code transcodeInto} directly into the scope's segment, closes the scope.</li>
 *   <li>{@link UploadOp.TranscodeIndices}: acquires a write scope, calls the index stream's
 *       {@code transcodeInto} directly into the scope's segment, closes the scope.</li>
 * </ul>
 *
 * <p>After all ops, the batch is flushed and the resulting completion is returned.
 */
public final class TransferBatchExecutor implements UploadExecutor {

    @Override
    public GpuCompletion execute(UploadPlan plan, VkQueue queue) {
        for (UploadOp op : plan.ops()) {
            switch (op) {
                case UploadOp.HostCopy hc -> {
                    try (BufferWriteScope scope = hc.dst().acquireWrite(hc.dstOffset(), hc.size(), queue)) {
                        MemorySegment.copy(hc.src(), hc.srcOffset(), scope.segment(), 0, hc.size());
                    }
                }
                case UploadOp.DeviceCopy dc -> {
                    dc.src().copyTo(dc.dst(), dc.srcOffset(), dc.dstOffset(), dc.size(), queue);
                }
                case UploadOp.Transcode tc -> {
                    long totalBytes = tc.dstStride() * tc.elementCount();
                    // The scope covers the full strided region starting at dstOffset.
                    // The transcode writes at relative offsets within that region.
                    long scopeOffset = tc.dstOffset();
                    try (BufferWriteScope scope = tc.dst().acquireWrite(scopeOffset, totalBytes, queue)) {
                        // transcodeInto writes at dstOffset=0 relative to the scope's segment
                        // since the scope already starts at the correct buffer position.
                        tc.source().transcodeInto(tc.targetLayout(), scope.segment(), 0,
                                tc.dstStride(), tc.firstElement(), tc.elementCount());
                    }
                }
                case UploadOp.TranscodeIndices ti -> {
                    long totalBytes = (long) ti.targetWidth().byteSize() * ti.indexCount();
                    try (BufferWriteScope scope = ti.dst().acquireWrite(ti.dstOffset(), totalBytes, queue)) {
                        ti.source().transcodeInto(ti.targetWidth(), ti.vertexBaseOffset(),
                                scope.segment(), 0, ti.firstIndex(), ti.indexCount());
                    }
                }
            }
        }
        // Flush the batch and return the completion for all recorded work.
        return TransferBatchManager.flush(queue.device(), queue);
    }
}
