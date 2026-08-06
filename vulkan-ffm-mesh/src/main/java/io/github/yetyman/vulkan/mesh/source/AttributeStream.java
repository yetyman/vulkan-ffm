package io.github.yetyman.vulkan.mesh.source;

import io.github.yetyman.vulkan.mesh.AttributeFormat;
import io.github.yetyman.vulkan.mesh.AttributeSemantic;
import io.github.yetyman.vulkan.mesh.DeviceRange;
import io.github.yetyman.vulkan.mesh.MeshLayout;

import java.lang.foreign.MemorySegment;
import java.util.Optional;

/**
 * One attribute's worth of data from one geometry source.
 *
 * <p>The primary operation is {@link #transcodeInto}: writing a window of elements directly into
 * caller-provided memory, in the caller's target layout, with format conversion applied. The caller
 * provides the destination (typically from {@code IBuffer.acquireWrite}), the target stride, and the
 * element window, so the source writes exactly once with no intermediate buffer.
 *
 * <p>Residency is a state, not a type: a stream's data may be host-readable, device-resident, both,
 * or absent. If those were separate types, every transition would be an object replacement.
 */
public interface AttributeStream {

    /**
     * @return which attribute this stream carries
     */
    AttributeSemantic semantic();

    /**
     * @return the format the source data is natively encoded in
     */
    AttributeFormat sourceFormat();

    /**
     * @return number of elements in this stream
     */
    long elementCount();

    /**
     * @return current residency state
     */
    Residency residency();

    /**
     * @return true if {@link #transcodeInto} can be called (i.e. host-readable data exists)
     */
    boolean isHostReadable();

    /**
     * @return the device-resident range, if any. Empty when not device-resident.
     */
    Optional<DeviceRange> deviceRange();

    /**
     * Transcodes a window of elements from this stream's native format into the target layout's
     * format for the same semantic, writing into {@code dst} at {@code dstOffset} with
     * {@code dstStride} between elements.
     *
     * <p>The element window ({@code firstElement}, {@code elementCount}) makes this parallelizable
     * across threads, enables partial residency (upload only the elements needed now), and lets
     * meshes larger than available staging memory be uploaded in chunks.
     *
     * <p>When the source format matches the target format for this semantic, the operation reduces
     * to a strided memory copy. When they differ (quantization, packing change), a per-element
     * conversion is applied.
     *
     * @param targetLayout  the destination layout; the target format and stride for this stream's
     *                      semantic are derived from it
     * @param dst           destination memory to write into
     * @param dstOffset     byte offset of the first element in {@code dst}
     * @param dstStride     bytes between consecutive elements in the destination
     * @param firstElement  first element index to transcode (0-based)
     * @param elementCount  number of elements to transcode
     * @throws IllegalStateException if not host-readable
     */
    void transcodeInto(MeshLayout targetLayout, MemorySegment dst, long dstOffset,
                       long dstStride, long firstElement, long elementCount);
}
