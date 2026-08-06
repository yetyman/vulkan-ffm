package io.github.yetyman.vulkan.mesh.source;

import io.github.yetyman.vulkan.mesh.DeviceRange;
import io.github.yetyman.vulkan.mesh.IndexWidth;

import java.lang.foreign.MemorySegment;
import java.util.Optional;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_SHORT_UNALIGNED;

/**
 * An {@link IndexStream} backed by a tightly-packed {@link MemorySegment}.
 *
 * <p>When source and target widths match and the base offset is zero, transcoding is a flat
 * {@link MemorySegment#copy}. Otherwise a per-index conversion handles width widening/narrowing
 * and base-offset application.
 */
public final class SegmentIndexStream implements IndexStream {

    private final IndexWidth width;
    private final long indexCount;
    private final MemorySegment data;
    private final long dataOffset;

    /**
     * @param width      width of each index in the backing segment
     * @param indexCount number of indices
     * @param data       backing native memory
     * @param dataOffset byte offset of the first index within {@code data}
     */
    public SegmentIndexStream(IndexWidth width, long indexCount, MemorySegment data, long dataOffset) {
        if (width == null) throw new IllegalArgumentException("width required");
        if (data == null) throw new IllegalArgumentException("data required");
        if (indexCount < 0) throw new IllegalArgumentException("indexCount must be >= 0");
        this.width = width;
        this.indexCount = indexCount;
        this.data = data;
        this.dataOffset = dataOffset;
    }

    /**
     * Convenience: starts at offset 0.
     */
    public SegmentIndexStream(IndexWidth width, long indexCount, MemorySegment data) {
        this(width, indexCount, data, 0);
    }

    @Override
    public IndexWidth sourceWidth() {
        return width;
    }

    @Override
    public long indexCount() {
        return indexCount;
    }

    @Override
    public Residency residency() {
        return Residency.HOST;
    }

    @Override
    public boolean isHostReadable() {
        return true;
    }

    @Override
    public Optional<DeviceRange> deviceRange() {
        return Optional.empty();
    }

    @Override
    public void transcodeInto(IndexWidth targetWidth, long vertexBaseOffset,
                              MemorySegment dst, long dstOffset,
                              long firstIndex, long indexCount) {
        if (!isHostReadable())
            throw new IllegalStateException("index stream is not host-readable");
        if (firstIndex < 0 || firstIndex + indexCount > this.indexCount)
            throw new IndexOutOfBoundsException("index window [" + firstIndex + ", "
                    + (firstIndex + indexCount) + ") exceeds indexCount " + this.indexCount);

        int srcByte = width.byteSize();
        int dstByte = targetWidth.byteSize();
        long srcPos = dataOffset + firstIndex * srcByte;
        long dstPos = dstOffset;

        // Fast path: same width, no base offset.
        if (width == targetWidth && vertexBaseOffset == 0) {
            MemorySegment.copy(data, srcPos, dst, dstPos, indexCount * srcByte);
            return;
        }

        // Per-index conversion.
        for (long i = 0; i < indexCount; i++) {
            long value = readIndex(srcPos) + vertexBaseOffset;
            writeIndex(dst, dstPos, targetWidth, value);
            srcPos += srcByte;
            dstPos += dstByte;
        }
    }

    private long readIndex(long pos) {
        return switch (width) {
            case U8 -> Byte.toUnsignedLong(data.get(JAVA_BYTE, pos));
            case U16 -> Short.toUnsignedLong(data.get(JAVA_SHORT_UNALIGNED, pos));
            case U32 -> Integer.toUnsignedLong(data.get(JAVA_INT_UNALIGNED, pos));
        };
    }

    private static void writeIndex(MemorySegment dst, long pos, IndexWidth targetWidth, long value) {
        switch (targetWidth) {
            case U8 -> dst.set(JAVA_BYTE, pos, (byte) value);
            case U16 -> dst.set(JAVA_SHORT_UNALIGNED, pos, (short) value);
            case U32 -> dst.set(JAVA_INT_UNALIGNED, pos, (int) value);
        }
    }

    /**
     * @return the raw backing segment
     */
    public MemorySegment rawData() {
        return data;
    }

    /**
     * @return byte offset of the first index within the raw data
     */
    public long rawOffset() {
        return dataOffset;
    }
}
