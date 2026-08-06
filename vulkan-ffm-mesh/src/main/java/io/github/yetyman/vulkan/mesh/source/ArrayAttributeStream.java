package io.github.yetyman.vulkan.mesh.source;

import io.github.yetyman.vulkan.mesh.AttributeFormat;
import io.github.yetyman.vulkan.mesh.AttributeSemantic;
import io.github.yetyman.vulkan.mesh.DeviceRange;
import io.github.yetyman.vulkan.mesh.MeshLayout;

import java.lang.foreign.MemorySegment;
import java.util.Optional;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED;

/**
 * An {@link AttributeStream} backed by a Java primitive array ({@code float[]} or {@code int[]}).
 *
 * <p>This is the convenience adapter for callers that already have their data in host arrays.
 * {@link #transcodeInto} writes from the array directly into the destination segment using
 * {@link MemorySegment#copy} intrinsics, avoiding intermediate buffers. It is not the primary
 * interface — {@link SegmentAttributeStream} over a native segment is always faster for large data
 * because it avoids the array bounds check per bulk copy — but it is simple and correct.
 *
 * <p>Supports two backing types:
 * <ul>
 *   <li>{@code float[]} for floating-point attributes (positions, normals, UVs, weights)</li>
 *   <li>{@code int[]} for integer attributes (joint indices, custom IDs)</li>
 * </ul>
 */
public final class ArrayAttributeStream implements AttributeStream {

    private final AttributeSemantic semantic;
    private final AttributeFormat format;
    private final long elementCount;
    private final int componentsPerElement;
    private final float[] floatData;
    private final int[] intData;

    /**
     * Creates a float-backed stream.
     *
     * @param semantic             attribute identity
     * @param format               element format (must be floating-point: F32xN)
     * @param componentsPerElement floats consumed per element
     * @param data                 backing array; length must be >= elementCount * componentsPerElement
     */
    public ArrayAttributeStream(AttributeSemantic semantic, AttributeFormat format,
                                int componentsPerElement, float[] data) {
        if (semantic == null) throw new IllegalArgumentException("semantic required");
        if (format == null) throw new IllegalArgumentException("format required");
        if (data == null) throw new IllegalArgumentException("data required");
        if (componentsPerElement <= 0) throw new IllegalArgumentException("componentsPerElement must be > 0");
        this.semantic = semantic;
        this.format = format;
        this.componentsPerElement = componentsPerElement;
        this.elementCount = data.length / componentsPerElement;
        this.floatData = data;
        this.intData = null;
    }

    /**
     * Creates an int-backed stream.
     *
     * @param semantic             attribute identity
     * @param format               element format (must be integer: U32xN or S32xN)
     * @param componentsPerElement ints consumed per element
     * @param data                 backing array; length must be >= elementCount * componentsPerElement
     */
    public ArrayAttributeStream(AttributeSemantic semantic, AttributeFormat format,
                                int componentsPerElement, int[] data) {
        if (semantic == null) throw new IllegalArgumentException("semantic required");
        if (format == null) throw new IllegalArgumentException("format required");
        if (data == null) throw new IllegalArgumentException("data required");
        if (componentsPerElement <= 0) throw new IllegalArgumentException("componentsPerElement must be > 0");
        this.semantic = semantic;
        this.format = format;
        this.componentsPerElement = componentsPerElement;
        this.elementCount = data.length / componentsPerElement;
        this.floatData = null;
        this.intData = data;
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

    @Override
    public void transcodeInto(MeshLayout targetLayout, MemorySegment dst, long dstOffset,
                              long dstStride, long firstElement, long elementCount) {
        if (firstElement < 0 || firstElement + elementCount > this.elementCount)
            throw new IndexOutOfBoundsException("element window exceeds stream size");

        AttributeFormat targetFormat = targetLayout.formatOf(semantic);
        int srcSize = format.byteSize();
        int dstSize = targetFormat.byteSize();

        if (srcSize != dstSize || !format.componentType().equals(targetFormat.componentType())
                || format.componentCount() != targetFormat.componentCount()) {
            throw new UnsupportedOperationException(
                    "Format conversion from " + format + " to " + targetFormat
                    + " not yet implemented for ArrayAttributeStream");
        }

        long dstPos = dstOffset;
        int srcIdx = (int) firstElement * componentsPerElement;

        if (floatData != null) {
            for (long e = 0; e < elementCount; e++) {
                for (int c = 0; c < componentsPerElement; c++) {
                    dst.set(JAVA_FLOAT_UNALIGNED, dstPos + (long) c * 4, floatData[srcIdx + c]);
                }
                srcIdx += componentsPerElement;
                dstPos += dstStride;
            }
        } else {
            for (long e = 0; e < elementCount; e++) {
                for (int c = 0; c < componentsPerElement; c++) {
                    dst.set(JAVA_INT_UNALIGNED, dstPos + (long) c * 4, intData[srcIdx + c]);
                }
                srcIdx += componentsPerElement;
                dstPos += dstStride;
            }
        }
    }
}
