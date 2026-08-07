package io.github.yetyman.vulkan.mesh.residency;

import io.github.yetyman.vulkan.mesh.AttributeSemantic;
import io.github.yetyman.vulkan.mesh.DeviceRange;
import io.github.yetyman.vulkan.mesh.IndexWidth;
import io.github.yetyman.vulkan.mesh.MeshLayout;
import io.github.yetyman.vulkan.mesh.source.AttributeStream;
import io.github.yetyman.vulkan.mesh.source.GeometrySource;
import io.github.yetyman.vulkan.mesh.source.IndexStream;
import io.github.yetyman.vulkan.mesh.source.SegmentAttributeStream;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Builds an {@link UploadPlan} from a source, a target layout, and an allocation.
 *
 * <p>All the logic, no side effects, no Vulkan calls. Responsibilities:
 * <ul>
 *   <li>Detect the identity case (source layout equals target layout) and emit one
 *       {@link UploadOp.HostCopy} per stream instead of per-attribute transcodes</li>
 *   <li>Emit per-attribute {@link UploadOp.Transcode} otherwise</li>
 *   <li>Emit {@link UploadOp.TranscodeIndices} with the correct base offset from the allocator</li>
 *   <li>Carry the scheduling hints through to the plan</li>
 * </ul>
 */
public final class UploadPlanner {

    private int dstAccessMask = 0x00000004 | 0x00000002; // VERTEX_ATTRIBUTE_READ | INDEX_READ
    private int dstStageMask = 0x00000004; // VERTEX_INPUT
    private QueueClass preferredQueue = QueueClass.TRANSFER;
    private Priority priority = Priority.NORMAL;
    private boolean deferrable = false;

    public UploadPlanner dstAccessMask(int mask) { this.dstAccessMask = mask; return this; }
    public UploadPlanner dstStageMask(int mask) { this.dstStageMask = mask; return this; }
    public UploadPlanner preferredQueue(QueueClass q) { this.preferredQueue = q; return this; }
    public UploadPlanner priority(Priority p) { this.priority = p; return this; }
    public UploadPlanner deferrable(boolean d) { this.deferrable = d; return this; }

    /**
     * Plans the upload of a full geometry into an allocation.
     *
     * @param source      the geometry data
     * @param targetLayout the layout the data should arrive in on the GPU
     * @param allocation   where the data should land
     * @param allocator    the allocator (for index base mode)
     * @return an executable plan
     */
    public UploadPlan plan(GeometrySource source, MeshLayout targetLayout,
                           GeometryAllocation allocation, GeometryAllocator allocator) {
        return plan(source, targetLayout, allocation, allocator, 0, source.elementCount());
    }

    /**
     * Plans the upload of a window of a geometry.
     *
     * @param firstElement first element in the upload window
     * @param elementCount number of elements to upload
     */
    public UploadPlan plan(GeometrySource source, MeshLayout targetLayout,
                           GeometryAllocation allocation, GeometryAllocator allocator,
                           long firstElement, long elementCount) {
        List<UploadOp> ops = new ArrayList<>();

        Optional<MeshLayout> nativeOpt = source.nativeLayout();
        boolean identityLayout = nativeOpt.isPresent() && nativeOpt.get().isIdenticalTo(targetLayout);

        if (identityLayout) {
            // Identity fast path: one flat copy per stream.
            for (int s = 0; s < targetLayout.streamCount(); s++) {
                long stride = targetLayout.strideOf(s);
                long srcOff = firstElement * stride;
                long size = elementCount * stride;
                DeviceRange dstRange = allocation.vertexRange(s);

                // Find any attribute in this stream to get at the raw segment
                AttributeStream representative = null;
                for (AttributeSemantic sem : targetLayout.semantics()) {
                    if (targetLayout.streamOf(sem) == s) {
                        representative = source.stream(sem);
                        break;
                    }
                }
                if (representative instanceof SegmentAttributeStream seg) {
                    ops.add(new UploadOp.HostCopy(
                            seg.rawData(), seg.rawOffset() + srcOff,
                            dstRange.buffer(), dstRange.offset() + srcOff, size));
                } else {
                    // Source does not expose raw segments; fall through to per-attribute transcode.
                    emitTranscodeOps(ops, source, targetLayout, allocation, s, firstElement, elementCount);
                }
            }
        } else {
            // Per-attribute transcode: each attribute is transcoded independently.
            for (int s = 0; s < targetLayout.streamCount(); s++) {
                emitTranscodeOps(ops, source, targetLayout, allocation, s, firstElement, elementCount);
            }
        }

        // Indices
        Optional<IndexStream> idxOpt = source.indices();
        if (idxOpt.isPresent() && allocation.indexRange().isPresent()) {
            IndexStream idx = idxOpt.get();
            DeviceRange idxRange = allocation.indexRange().get();
            IndexWidth targetWidth = idx.sourceWidth();
            long vertexBase = (allocator.indexBaseMode() == IndexBaseMode.REWRITE_ABSOLUTE)
                    ? allocation.vertexBase() : 0;
            ops.add(new UploadOp.TranscodeIndices(idx, targetWidth, vertexBase,
                    idxRange.buffer(), idxRange.offset(), 0, idx.indexCount()));
        }

        return new UploadPlan(ops, dstAccessMask, dstStageMask, preferredQueue, priority, deferrable);
    }

    private void emitTranscodeOps(List<UploadOp> ops, GeometrySource source,
                                  MeshLayout targetLayout, GeometryAllocation allocation,
                                  int streamId, long firstElement, long elementCount) {
        DeviceRange dstRange = allocation.vertexRange(streamId);
        long dstStride = targetLayout.strideOf(streamId);

        for (MeshLayout.Placement p : targetLayout.placementsIn(streamId)) {
            if (!source.available().contains(p.semantic())) continue;
            AttributeStream stream = source.stream(p.semantic());
            long dstOff = dstRange.offset() + p.offset() + firstElement * dstStride;
            ops.add(new UploadOp.Transcode(stream, p.semantic(), targetLayout,
                    dstRange.buffer(), dstOff, dstStride, firstElement, elementCount));
        }
    }
}
