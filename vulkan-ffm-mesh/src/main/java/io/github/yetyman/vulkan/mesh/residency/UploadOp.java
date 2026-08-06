package io.github.yetyman.vulkan.mesh.residency;

import io.github.yetyman.vulkan.buffers.IBuffer;
import io.github.yetyman.vulkan.mesh.AttributeSemantic;
import io.github.yetyman.vulkan.mesh.IndexWidth;
import io.github.yetyman.vulkan.mesh.MeshLayout;
import io.github.yetyman.vulkan.mesh.source.AttributeStream;
import io.github.yetyman.vulkan.mesh.source.IndexStream;

import java.lang.foreign.MemorySegment;

/**
 * One step in an upload plan. Two distinct shapes because copying and transcoding are genuinely
 * different operations, not variants of one.
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
     * Layout-converting write for one attribute stream. The source writes directly into the
     * destination in the target layout, so no intermediate buffer exists.
     */
    record Transcode(AttributeStream source, AttributeSemantic semantic,
                     MeshLayout targetLayout,
                     IBuffer dst, long dstOffset, long dstStride,
                     long firstElement, long elementCount) implements UploadOp {
    }

    /**
     * Index transcode, including width conversion and vertex-base rewriting.
     */
    record TranscodeIndices(IndexStream source, IndexWidth targetWidth, long vertexBaseOffset,
                            IBuffer dst, long dstOffset,
                            long firstIndex, long indexCount) implements UploadOp {
    }
}
