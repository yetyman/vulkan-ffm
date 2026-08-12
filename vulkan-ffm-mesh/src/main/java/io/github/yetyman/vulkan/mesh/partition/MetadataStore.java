package io.github.yetyman.vulkan.mesh.partition;

import java.lang.foreign.MemorySegment;

/**
 * The bulk transfer contract shared by all metadata channel backing stores: typed object channels,
 * primitive float channels, primitive int channels, and future columnar-backed channels.
 *
 * <p>Every metadata store can:
 * <ul>
 *   <li>Report its per-element byte size (stride) and current element count</li>
 *   <li>Bulk-write a range of elements into a {@link MemorySegment} in GPU-ready layout</li>
 *   <li>Bulk-read a range of elements from a {@link MemorySegment} back into its backing store</li>
 * </ul>
 *
 * <p>For primitive-backed stores ({@link FloatMetadataChannel}, {@link IntMetadataChannel}), the
 * bulk operations are a single {@link MemorySegment#copy} with no per-element overhead. For
 * typed object stores ({@link MetadataChannel} + {@link PartitionMetadata}), the bulk operations
 * loop over elements calling {@link io.github.yetyman.vulkan.buffers.GpuLayout#writeTo} per element.
 *
 * <p>Consumers ({@link io.github.yetyman.vulkan.mesh.consume.GeometryTable}, upload planners)
 * accept this interface uniformly. They do not know or care whether the backing is a primitive
 * array or an object array — the performance difference is inherent to the data shape, not
 * exposed through the interface.
 */
public interface MetadataStore {

    /**
     * @return diagnostic name of this channel
     */
    String name();

    /**
     * @return bytes per element in GPU layout
     */
    int byteSize();

    /**
     * @return current number of elements in the store
     */
    int count();

    /**
     * Writes elements [{@code from}, {@code to}) into {@code dst} starting at {@code dstOffset},
     * in dense GPU-ready layout (stride = {@link #byteSize()}, no gaps).
     *
     * <p>For primitive-backed channels, this is a single bulk memory copy. For typed channels,
     * this is a per-element serialization loop. Both produce identical byte output.
     *
     * @param dst       destination segment (must have room for (to - from) * byteSize bytes)
     * @param dstOffset byte offset into dst where writing begins
     * @param from      first element index (inclusive)
     * @param to        last element index (exclusive)
     */
    void bulkWriteTo(MemorySegment dst, long dstOffset, int from, int to);

    /**
     * Reads elements [{@code from}, {@code to}) from {@code src} starting at {@code srcOffset}
     * back into this store's backing data.
     *
     * <p>For primitive-backed channels, this is a single bulk memory copy. For typed channels
     * with mutable values, this calls {@link io.github.yetyman.vulkan.buffers.GpuLayout#readFrom}
     * per element. For typed channels with immutable values (using
     * {@link io.github.yetyman.vulkan.buffers.GpuCodec}), this calls {@code decode} per element.
     *
     * @param src       source segment containing GPU-layout data
     * @param srcOffset byte offset into src where reading begins
     * @param from      first element index (inclusive)
     * @param to        last element index (exclusive)
     */
    void bulkReadFrom(MemorySegment src, long srcOffset, int from, int to);

    /**
     * Convenience: writes all elements into a new segment allocated from an auto arena.
     * Equivalent to allocating byteSize * count bytes and calling bulkWriteTo over [0, count).
     *
     * @return a segment containing the full serialized channel data
     */
    default MemorySegment toSegment() {
        long size = (long) byteSize() * count();
        MemorySegment seg = java.lang.foreign.Arena.ofAuto().allocate(size);
        bulkWriteTo(seg, 0, 0, count());
        return seg;
    }
}
