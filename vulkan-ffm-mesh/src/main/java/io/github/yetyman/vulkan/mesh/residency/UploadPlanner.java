package io.github.yetyman.vulkan.mesh.residency;

import io.github.yetyman.vulkan.mesh.AttributeSemantic;
import io.github.yetyman.vulkan.mesh.DeviceRange;
import io.github.yetyman.vulkan.mesh.ElementWindow;
import io.github.yetyman.vulkan.mesh.IndexWidth;
import io.github.yetyman.vulkan.mesh.MeshLayout;
import io.github.yetyman.vulkan.mesh.source.AttributeStream;
import io.github.yetyman.vulkan.mesh.source.GeometrySource;
import io.github.yetyman.vulkan.mesh.source.IndexStream;
import io.github.yetyman.vulkan.mesh.source.SegmentAttributeStream;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Builds an {@link UploadPlan} from a source, a target layout, and an allocation.
 *
 * <p>All the logic, no side effects, no Vulkan calls. Responsibilities:
 * <ul>
 *   <li>Detect the identity case (source layout equals target layout) and emit one
 *       {@link UploadOp.HostCopy} per stream instead of per-attribute transcodes</li>
 *   <li>Group attribute transcodes per stream, which is required for correctness on interleaved
 *       layouts -- see {@link UploadOp.TranscodeStream}</li>
 *   <li>Emit {@link UploadOp.TranscodeIndices} with the base offset the allocator's mode requires</li>
 *   <li>Carry scheduling hints through to the plan</li>
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
     * Plans the full upload of a geometry into an allocation, vertices and indices both.
     */
    public UploadPlan plan(GeometrySource source, MeshLayout targetLayout,
                           GeometryAllocation allocation, GeometryAllocator allocator) {
        return plan(source, targetLayout, allocation, allocator,
                ElementWindow.all(source.elementCount()));
    }

    /**
     * Plans the upload of an element window of a geometry, vertices and indices both.
     *
     * <p>Indices are only included when the window covers the whole geometry, because a partial
     * vertex window says nothing about which indices reference it. Use
     * {@link #planIndexUpdate} to re-send indices independently.
     */
    public UploadPlan plan(GeometrySource source, MeshLayout targetLayout,
                           GeometryAllocation allocation, GeometryAllocator allocator,
                           ElementWindow window) {
        List<UploadOp> ops = new ArrayList<>();

        boolean wholeGeometry = window.firstElement() == 0
                && window.elementCount() >= source.elementCount();

        Optional<MeshLayout> nativeOpt = source.nativeLayout();
        boolean identityLayout = nativeOpt.isPresent() && nativeOpt.get().isIdenticalTo(targetLayout);

        for (int s = 0; s < targetLayout.streamCount(); s++) {
            if (identityLayout && emitIdentityCopy(ops, source, targetLayout, allocation, s, window)) {
                continue;
            }
            emitStreamTranscode(ops, source, targetLayout, allocation, s, window);
        }

        if (wholeGeometry) {
            emitIndexTranscode(ops, source, allocation, allocator, null);
        }

        return new UploadPlan(ops, dstAccessMask, dstStageMask, preferredQueue, priority, deferrable);
    }

    /**
     * Plans a partial re-upload of specific attributes over an element window, for geometry that
     * has already been uploaded and has since changed.
     *
     * <p>Attributes are grouped by the stream they live in. When the requested set does not cover
     * every attribute of a stream, the executor must preserve that stream's other bytes, which is
     * markedly slower. Placing mutable attributes in their own stream makes each update a single
     * contiguous copy; see {@code MeshLayout} on hybrid arrangements.
     *
     * @param semantics which attributes changed
     * @param window    which elements changed
     */
    public UploadPlan planUpdate(GeometrySource source, MeshLayout targetLayout,
                                 GeometryAllocation allocation,
                                 Set<AttributeSemantic> semantics, ElementWindow window) {
        List<UploadOp> ops = new ArrayList<>();
        if (window.isEmpty() || semantics.isEmpty()) {
            return new UploadPlan(ops, dstAccessMask, dstStageMask, preferredQueue, priority, deferrable);
        }

        // Group the requested semantics by stream so each stream commits as one write.
        Set<Integer> touchedStreams = new LinkedHashSet<>();
        for (AttributeSemantic sem : semantics) {
            if (!targetLayout.has(sem)) continue;
            touchedStreams.add(targetLayout.streamOf(sem));
        }

        for (int streamId : touchedStreams) {
            emitStreamTranscode(ops, source, targetLayout, allocation, streamId, window, semantics);
        }

        return new UploadPlan(ops, dstAccessMask, dstStageMask, preferredQueue, priority, deferrable);
    }

    /**
     * Plans a re-upload of the index stream alone, for geometry whose topology changed while its
     * vertex allocation stayed valid.
     */
    public UploadPlan planIndexUpdate(GeometrySource source, GeometryAllocation allocation,
                                      GeometryAllocator allocator, ElementWindow indexWindow) {
        List<UploadOp> ops = new ArrayList<>();
        emitIndexTranscode(ops, source, allocation, allocator, indexWindow);
        return new UploadPlan(ops, dstAccessMask, dstStageMask, preferredQueue, priority, deferrable);
    }

    // -------------------------------------------------------------------------
    // Emission
    // -------------------------------------------------------------------------

    /**
     * Emits a flat copy for a stream when the source exposes raw native memory in the identical
     * layout. Returns false when the source cannot provide a raw segment, so the caller falls back
     * to transcoding.
     */
    private boolean emitIdentityCopy(List<UploadOp> ops, GeometrySource source,
                                     MeshLayout targetLayout, GeometryAllocation allocation,
                                     int streamId, ElementWindow window) {
        AttributeStream representative = null;
        for (MeshLayout.Placement p : targetLayout.placementsIn(streamId)) {
            if (!source.available().contains(p.semantic())) continue;
            representative = source.stream(p.semantic());
            break;
        }
        if (!(representative instanceof SegmentAttributeStream seg)) return false;

        long stride = targetLayout.strideOf(streamId);
        DeviceRange dstRange = allocation.vertexRange(streamId);
        long srcOff = seg.rawOffset() + window.firstElement() * seg.rawStride();
        long dstOff = dstRange.offset() + window.firstElement() * stride;
        long size = window.elementCount() * stride;
        if (size <= 0) return true;

        ops.add(new UploadOp.HostCopy(seg.rawData(), srcOff, dstRange.buffer(), dstOff, size));
        return true;
    }

    private void emitStreamTranscode(List<UploadOp> ops, GeometrySource source,
                                     MeshLayout targetLayout, GeometryAllocation allocation,
                                     int streamId, ElementWindow window) {
        emitStreamTranscode(ops, source, targetLayout, allocation, streamId, window, null);
    }

    /**
     * Emits one grouped transcode op for a stream.
     *
     * @param restrictTo when non-null, only these semantics are written; the op is then marked as
     *                   not covering all attributes so the executor preserves the rest
     */
    private void emitStreamTranscode(List<UploadOp> ops, GeometrySource source,
                                     MeshLayout targetLayout, GeometryAllocation allocation,
                                     int streamId, ElementWindow window,
                                     Set<AttributeSemantic> restrictTo) {
        if (window.isEmpty()) return;

        List<MeshLayout.Placement> inStream = targetLayout.placementsIn(streamId);
        List<UploadOp.StreamAttribute> attrs = new ArrayList<>();
        int available = 0;

        for (MeshLayout.Placement p : inStream) {
            boolean sourceHas = source.available().contains(p.semantic());
            if (sourceHas) available++;
            if (!sourceHas) continue;
            if (restrictTo != null && !restrictTo.contains(p.semantic())) continue;
            attrs.add(new UploadOp.StreamAttribute(
                    p.semantic(), source.stream(p.semantic()), p.offset()));
        }

        if (attrs.isEmpty()) return;

        // The op covers everything the layout places here only if we are writing every placement
        // in the stream. Attributes the source lacks also leave gaps, so they count against it.
        boolean coversAll = attrs.size() == inStream.size();

        DeviceRange dstRange = allocation.vertexRange(streamId);
        ops.add(new UploadOp.TranscodeStream(
                attrs, targetLayout, streamId,
                dstRange.buffer(), dstRange.offset(), targetLayout.strideOf(streamId),
                window.firstElement(), window.elementCount(), coversAll));
    }

    private void emitIndexTranscode(List<UploadOp> ops, GeometrySource source,
                                    GeometryAllocation allocation, GeometryAllocator allocator,
                                    ElementWindow indexWindow) {
        Optional<IndexStream> idxOpt = source.indices();
        if (idxOpt.isEmpty() || allocation.indexRange().isEmpty()) return;

        IndexStream idx = idxOpt.get();
        DeviceRange idxRange = allocation.indexRange().get();
        IndexWidth targetWidth = idx.sourceWidth();

        long vertexBase = (allocator != null
                && allocator.indexBaseMode() == IndexBaseMode.REWRITE_ABSOLUTE)
                ? allocation.vertexBase() : 0;

        long first = indexWindow != null ? indexWindow.firstElement() : 0;
        long count = indexWindow != null ? indexWindow.elementCount() : idx.indexCount();
        if (count <= 0) return;

        long dstOff = idxRange.offset() + first * targetWidth.byteSize();
        ops.add(new UploadOp.TranscodeIndices(idx, targetWidth, vertexBase,
                idxRange.buffer(), dstOff, first, count));
    }
}
