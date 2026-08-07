package io.github.yetyman.vulkan.mesh.residency;

import io.github.yetyman.vulkan.VkQueue;
import io.github.yetyman.vulkan.buffers.BufferReadScope;
import io.github.yetyman.vulkan.buffers.BufferWriteScope;
import io.github.yetyman.vulkan.buffers.GpuCompletion;
import io.github.yetyman.vulkan.buffers.TransferBatchManager;

import java.lang.foreign.MemorySegment;

/**
 * Executes an {@link UploadPlan} using the per-thread {@link TransferBatchManager} infrastructure.
 *
 * <p>For each op:
 * <ul>
 *   <li>{@link UploadOp.HostCopy}: acquires a write scope, copies host bytes in, closes it.</li>
 *   <li>{@link UploadOp.DeviceCopy}: records a device-to-device copy into the batch.</li>
 *   <li>{@link UploadOp.TranscodeStream}: acquires one write scope for the whole stream window and
 *       has every attribute transcode directly into it, so a single copy commits all of them.</li>
 *   <li>{@link UploadOp.TranscodeIndices}: acquires a write scope and transcodes indices into it.</li>
 * </ul>
 *
 * <p>After all ops the batch is flushed and the resulting completion returned. Nothing here blocks;
 * the caller decides whether to await the completion or let a later submission wait on it.
 */
public final class TransferBatchExecutor implements UploadExecutor {

    private static boolean warnedAboutPartialStream = false;

    @Override
    public GpuCompletion execute(UploadPlan plan, VkQueue queue) {
        for (UploadOp op : plan.ops()) {
            switch (op) {
                case UploadOp.HostCopy hc -> {
                    if (hc.size() <= 0) break;
                    try (BufferWriteScope scope = hc.dst().acquireWrite(hc.dstOffset(), hc.size(), queue)) {
                        MemorySegment.copy(hc.src(), hc.srcOffset(), scope.segment(), 0, hc.size());
                    }
                }
                case UploadOp.DeviceCopy dc -> {
                    if (dc.size() <= 0) break;
                    dc.src().copyTo(dc.dst(), dc.srcOffset(), dc.dstOffset(), dc.size(), queue);
                }
                case UploadOp.TranscodeStream ts -> executeStream(ts, queue);
                case UploadOp.TranscodeIndices ti -> {
                    long totalBytes = (long) ti.targetWidth().byteSize() * ti.indexCount();
                    if (totalBytes <= 0) break;
                    try (BufferWriteScope scope = ti.dst().acquireWrite(ti.dstOffset(), totalBytes, queue)) {
                        ti.source().transcodeInto(ti.targetWidth(), ti.vertexBaseOffset(),
                                scope.segment(), 0, ti.firstIndex(), ti.indexCount());
                    }
                }
            }
        }
        return TransferBatchManager.flush(queue.device(), queue);
    }

    /**
     * Writes every attribute of one stream window into a single acquired region, so the staged
     * commit is one contiguous copy that carries all of them.
     *
     * <p>When the op does not cover every attribute the layout places in this stream, the
     * destination's current contents are read back into the staging region first, so the untouched
     * attributes survive the copy. That readback stalls the pipeline, which is why the fast path
     * matters: give mutable attributes their own stream and updates stay on it.
     */
    private void executeStream(UploadOp.TranscodeStream ts, VkQueue queue) {
        if (ts.elementCount() <= 0 || ts.attributes().isEmpty()) return;

        long windowBytes = ts.dstStride() * ts.elementCount();
        long windowOffset = ts.streamBaseOffset() + ts.firstElement() * ts.dstStride();

        try (BufferWriteScope scope = ts.dst().acquireWrite(windowOffset, windowBytes, queue)) {
            if (!ts.coversAllAttributes()) {
                preserveExisting(ts, scope, windowOffset, windowBytes, queue);
            }
            for (UploadOp.StreamAttribute attr : ts.attributes()) {
                // Within the scope, element i of this attribute sits at
                // i * stride + offsetInElement, so the attribute's own offset is the base.
                attr.source().transcodeInto(ts.targetLayout(), scope.segment(),
                        attr.offsetInElement(), ts.dstStride(),
                        ts.firstElement(), ts.elementCount());
            }
        }
    }

    /**
     * Copies the destination's current bytes into the staging region so attributes not present in
     * this op are not clobbered by the commit.
     */
    private void preserveExisting(UploadOp.TranscodeStream ts, BufferWriteScope scope,
                                  long windowOffset, long windowBytes, VkQueue queue) {
        if (!warnedAboutPartialStream) {
            warnedAboutPartialStream = true;
            System.err.println("WARNING: partial-attribute update of stream " + ts.streamId()
                    + " requires a read-modify-write and stalls the pipeline. "
                    + "Place mutable attributes in their own MeshLayout stream to avoid this.");
        }
        try (BufferReadScope read = ts.dst().acquireRead(windowOffset, windowBytes, queue)) {
            MemorySegment.copy(read.segment(), 0, scope.segment(), 0, windowBytes);
        }
    }
}
