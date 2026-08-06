package io.github.yetyman.vulkan.mesh.source;

import io.github.yetyman.vulkan.mesh.AttributeFormat;
import io.github.yetyman.vulkan.mesh.AttributeSemantic;
import io.github.yetyman.vulkan.mesh.DeviceRange;
import io.github.yetyman.vulkan.mesh.MeshLayout;

import java.lang.foreign.MemorySegment;
import java.util.Optional;

/**
 * An {@link AttributeStream} backed by a {@link MemorySegment}: either an arena-owned buffer, a
 * memory-mapped file region, or any other native memory the caller supplies. This is the fast
 * common case for file-format adapters (the file's vertex data, possibly memory-mapped) and for
 * pre-computed arrays already in native memory.
 *
 * <p>When the source format matches the target format for this semantic, {@link #transcodeInto}
 * reduces to a strided {@link MemorySegment#copy}. Format conversion is not yet implemented;
 * it will be added as specific conversions are needed (e.g. F32x3 to S16x4_NORM quantization).
 */
public final class SegmentAttributeStream implements AttributeStream {

    private final AttributeSemantic semantic;
    private final AttributeFormat format;
    private final long elementCount;
    private final MemorySegment data;
    private final long dataOffset;
    private final long dataStride;

    /**
     * @param semantic     which attribute this carries
     * @param format       how each element is encoded in the backing segment
     * @param elementCount number of elements
     * @param data         the backing native memory
     * @param dataOffset   byte offset of element 0 within {@code data}
     * @param dataStride   bytes between consecutive elements in the source (may differ from
     *                     {@code format.byteSize()} for interleaved sources)
     */
    public SegmentAttributeStream(AttributeSemantic semantic, AttributeFormat format,
                                  long elementCount, MemorySegment data,
                                  long dataOffset, long dataStride) {
        if (semantic == null) throw new IllegalArgumentException("semantic required");
        if (format == null) throw new IllegalArgumentException("format required");
        if (data == null) throw new IllegalArgumentException("data required");
        if (elementCount < 0) throw new IllegalArgumentException("elementCount must be >= 0");
        if (dataStride <= 0) throw new IllegalArgumentException("dataStride must be > 0");
        this.semantic = semantic;
        this.format = format;
        this.elementCount = elementCount;
        this.data = data;
        this.dataOffset = dataOffset;
        this.dataStride = dataStride;
    }

    /**
     * Convenience: tightly packed (stride == format.byteSize()), starting at offset 0.
     */
    public SegmentAttributeStream(AttributeSemantic semantic, AttributeFormat format,
                                  long elementCount, MemorySegment data) {
        this(semantic, format, elementCount, data, 0, format.byteSize());
    }

    @Override
    public AttributeSemantic semantic() {
        return semantic;
    }

    @Override
    public AttributeFormat sourceFormat() {
        return format;
    }

    @Override
    public long elementCount() {
        return elementCount;
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

    /**
     * Transcodes elements into the destination. When source and target formats match, this is a
     * strided memory copy with no per-element logic; when they differ, a per-element conversion is
     * applied (stub: currently throws for mismatched formats until specific converters are added).
     */
    @Override
    public void transcodeInto(MeshLayout targetLayout, MemorySegment dst, long dstOffset,
                              long dstStride, long firstElement, long elementCount) {
        if (!isHostReadable())
            throw new IllegalStateException("stream is not host-readable");
        if (firstElement < 0 || firstElement + elementCount > this.elementCount)
            throw new IndexOutOfBoundsException("element window [" + firstElement + ", "
                    + (firstElement + elementCount) + ") exceeds elementCount " + this.elementCount);

        AttributeFormat targetFormat = targetLayout.formatOf(semantic);
        int srcSize = format.byteSize();
        int dstSize = targetFormat.byteSize();

        if (format.equals(targetFormat) || (srcSize == dstSize && !isConversionNeeded(targetFormat))) {
            // Fast path: same encoding, strided copy.
            long srcPos = dataOffset + firstElement * dataStride;
            long dstPos = dstOffset;
            for (long i = 0; i < elementCount; i++) {
                MemorySegment.copy(data, srcPos, dst, dstPos, srcSize);
                srcPos += dataStride;
                dstPos += dstStride;
            }
        } else {
            // Format conversion needed. This will be implemented per-pair as needed.
            throw new UnsupportedOperationException(
                    "Format conversion from " + format + " to " + targetFormat
                    + " is not yet implemented for semantic '" + semantic + "'. "
                    + "Ensure source and target formats match, or add the converter.");
        }
    }

    /**
     * @return the raw backing segment, for callers that need direct access (e.g. computing bounds)
     */
    public MemorySegment rawData() {
        return data;
    }

    /**
     * @return byte offset of element 0 within the raw data
     */
    public long rawOffset() {
        return dataOffset;
    }

    /**
     * @return bytes between elements in the raw data
     */
    public long rawStride() {
        return dataStride;
    }

    private boolean isConversionNeeded(AttributeFormat targetFormat) {
        // For now, same componentType + count + normalized means no conversion needed
        return format.componentType() != targetFormat.componentType()
                || format.componentCount() != targetFormat.componentCount()
                || format.normalized() != targetFormat.normalized();
    }
}
