package io.github.yetyman.vulkan.mesh;

import io.github.yetyman.vulkan.buffers.IBuffer;

/**
 * A byte range within a GPU buffer, optionally strided.
 *
 * <p>The unit of "where does this data actually live" throughout the mesh module. Deliberately a
 * plain record over {@link IBuffer}: it carries no ownership, so the same range can be described,
 * passed around, and bound without anyone wondering who closes it. The buffer is owned by whatever
 * allocator produced it.
 *
 * @param buffer the backing buffer
 * @param offset byte offset of the first element within the buffer
 * @param size   total byte length of the range
 * @param stride bytes between consecutive elements, or 0 when the range is not element-structured
 */
public record DeviceRange(IBuffer buffer, long offset, long size, long stride) {

    public DeviceRange {
        if (buffer == null) throw new IllegalArgumentException("buffer required");
        if (offset < 0) throw new IllegalArgumentException("offset must be >= 0");
        if (size < 0) throw new IllegalArgumentException("size must be >= 0");
        if (stride < 0) throw new IllegalArgumentException("stride must be >= 0");
    }

    /**
     * Creates an unstrided range covering the whole buffer.
     */
    public static DeviceRange whole(IBuffer buffer) {
        return new DeviceRange(buffer, 0, buffer.size(), 0);
    }

    /**
     * @return the number of elements this range holds at its stride, or -1 when unstrided
     */
    public long elementCount() {
        return stride == 0 ? -1 : size / stride;
    }

    /**
     * @return byte offset of element {@code index}, relative to the buffer
     */
    public long offsetOf(long index) {
        if (stride == 0) throw new IllegalStateException("range is not element-structured (stride 0)");
        return offset + index * stride;
    }

    /**
     * @return a sub-range of this range, in elements. Requires a stride.
     */
    public DeviceRange slice(long firstElement, long elementCount) {
        if (stride == 0) throw new IllegalStateException("range is not element-structured (stride 0)");
        return new DeviceRange(buffer, offset + firstElement * stride, elementCount * stride, stride);
    }
}
