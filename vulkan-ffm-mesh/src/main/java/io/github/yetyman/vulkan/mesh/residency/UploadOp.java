package io.github.yetyman.vulkan.mesh.residency;

import io.github.yetyman.vulkan.buffers.IBuffer;
import io.github.yetyman.vulkan.mesh.AttributeSemantic;
import io.github.yetyman.vulkan.mesh.IndexWidth;
import io.github.yetyman.vulkan.mesh.MeshLayout;
import io.github.yetyman.vulkan.mesh.source.AttributeStream;
import io.github.yetyman.vulkan.mesh.source.IndexStream;

import java.lang.foreign.MemorySegment;
import java.util.List;

/**
 * One step in an upload plan. Copying and transcoding are genuinely different operations rather
 * than variants of one, so they are separate shapes.
 */
public sealed interface UploadOp {

    /**
     * Straight byte copy from host memory to a device buffer range.
     * Used when the source is already in the target layout (identity-layout fast path).
     */
    record HostCopy(MemorySegment src, long srcOffset,
                    IBuffer dst, long dstOffset, long size) implements UploadOp {
    }

    /**
     * Device-to-device copy. Used for repacking, defragmentation, and GPU-produced geometry.
     */
    record DeviceCopy(IBuffer src, long srcOffset,
                      IBuffer dst, long dstOffset, long size) implements UploadOp {
    }

    /**
     * One attribute's contribution to a stream transcode: which semantic, where its source data
     * comes from, and its byte offset within the destination element.
     */
    record StreamAttribute(AttributeSemantic semantic, AttributeStream source, long offsetInElement) {
    }

    /**
     * Layout-converting write of one or more attributes into a single vertex stream.
     *
     * <p>Attributes are grouped per stream rather than emitted individually, and this is not a
     * convenience -- it is required for correctness. A staged upload commits as one contiguous
     * {@code vkCmdCopyBuffer} over the destination range. If each attribute of an interleaved stream
     * were uploaded separately, every copy would span the full element stride and overwrite the
     * bytes belonging to its neighbours with uninitialized staging memory, so only the last
     * attribute written would survive. Writing all attributes of a stream into one staging region
     * before committing avoids that entirely.
     *
     * <p>{@link #coversAllAttributes} records whether the attribute list accounts for every
     * attribute the target layout places in this stream. When false, the destination's existing
     * contents must be preserved, which forces a slower read-modify-write path; see
     * {@code TransferBatchExecutor}. Keeping mutable attributes in their own stream avoids it.
     *
     * @param attributes         the attributes to write, each with its offset within the element
     * @param targetLayout       the layout being written into
     * @param streamId           which stream of the layout this targets
     * @param dst                destination buffer
     * @param streamBaseOffset   byte offset of element zero of this stream within {@code dst}
     * @param dstStride          bytes between consecutive elements
     * @param firstElement       first element of the window
     * @param elementCount       number of elements in the window
     * @param coversAllAttributes whether every attribute of this stream is included
     */
    record TranscodeStream(List<StreamAttribute> attributes,
                           MeshLayout targetLayout,
                           int streamId,
                           IBuffer dst, long streamBaseOffset, long dstStride,
                           long firstElement, long elementCount,
                           boolean coversAllAttributes) implements UploadOp {
        public TranscodeStream {
            attributes = List.copyOf(attributes);
        }
    }

    /**
     * Index transcode, including width conversion and vertex-base rewriting.
     */
    record TranscodeIndices(IndexStream source, IndexWidth targetWidth, long vertexBaseOffset,
                            IBuffer dst, long dstOffset,
                            long firstIndex, long indexCount) implements UploadOp {
    }
}
