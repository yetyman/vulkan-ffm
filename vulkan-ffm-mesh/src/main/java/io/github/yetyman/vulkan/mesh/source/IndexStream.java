package io.github.yetyman.vulkan.mesh.source;

import io.github.yetyman.vulkan.mesh.DeviceRange;
import io.github.yetyman.vulkan.mesh.IndexWidth;

import java.lang.foreign.MemorySegment;
import java.util.Optional;

/**
 * An index buffer source. Separate from {@link AttributeStream} because indices have their own
 * width, carry a vertex-base-offset rewriting concern, and have no semantic or format in the
 * attribute sense.
 *
 * <p>The primary operation is {@link #transcodeInto}: writing a window of indices into
 * caller-provided memory at a target width, with an optional vertex base offset applied. The base
 * offset is what makes shared global vertex pools work: indices stored relative to zero are
 * rewritten to be relative to the pool allocation.
 */
public interface IndexStream {

    /**
     * @return the width of each index in this stream's native encoding
     */
    IndexWidth sourceWidth();

    /**
     * @return number of indices in this stream
     */
    long indexCount();

    /**
     * @return current residency state
     */
    Residency residency();

    /**
     * @return true if {@link #transcodeInto} can be called
     */
    boolean isHostReadable();

    /**
     * @return the device-resident range, if any
     */
    Optional<DeviceRange> deviceRange();

    /**
     * Transcodes a window of indices into {@code dst} at {@code targetWidth}, adding
     * {@code vertexBaseOffset} to each index.
     *
     * <p>When source and target widths match and the base offset is zero, this reduces to a flat
     * memory copy. Otherwise a per-index conversion is applied.
     *
     * @param targetWidth       desired output width per index
     * @param vertexBaseOffset  value added to each index (0 when indices stay relative to zero)
     * @param dst               destination memory
     * @param dstOffset         byte offset of the first index in {@code dst}
     * @param firstIndex        first source index to transcode (0-based)
     * @param indexCount        number of indices to transcode
     * @throws IllegalStateException if not host-readable
     */
    void transcodeInto(IndexWidth targetWidth, long vertexBaseOffset,
                       MemorySegment dst, long dstOffset,
                       long firstIndex, long indexCount);
}
