package io.github.yetyman.vulkan.mesh.process;

import io.github.yetyman.helpers.math.Vec3;
import io.github.yetyman.helpers.math.geometry.AABB;
import io.github.yetyman.vulkan.mesh.AttributeFormat;
import io.github.yetyman.vulkan.mesh.AttributeSemantic;
import io.github.yetyman.vulkan.mesh.MeshLayout;
import io.github.yetyman.vulkan.mesh.source.AttributeStream;
import io.github.yetyman.vulkan.mesh.source.GeometrySource;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT_UNALIGNED;

/**
 * Computes axis-aligned bounding boxes from geometry position data.
 *
 * <p>Provides static utilities for computing bounds from an {@link AttributeStream} of positions
 * or directly from a {@link GeometrySource}. Useful when constructing geometry incrementally
 * or from sources that do not know their bounds at creation time.</p>
 */
public final class BoundsCalculator {

    private BoundsCalculator() {}

    /**
     * Computes the AABB for all positions in the given source.
     *
     * @param source the geometry source (must have POSITION, host-readable)
     * @return the axis-aligned bounding box
     */
    public static AABB compute(GeometrySource source) {
        if (!source.available().contains(AttributeSemantic.POSITION)) {
            throw new IllegalArgumentException("Source must have POSITION attribute");
        }
        return compute(source.stream(AttributeSemantic.POSITION));
    }

    /**
     * Computes the AABB from a position attribute stream.
     *
     * @param positionStream a stream carrying POSITION data (F32x3 expected, host-readable)
     * @return the axis-aligned bounding box
     */
    public static AABB compute(AttributeStream positionStream) {
        if (!positionStream.isHostReadable()) {
            throw new IllegalStateException("Position stream must be host-readable for bounds computation");
        }
        long count = positionStream.elementCount();
        if (count == 0) {
            return new AABB(new Vec3(0, 0, 0), new Vec3(0, 0, 0));
        }

        try (Arena arena = Arena.ofConfined()) {
            MeshLayout layout = MeshLayout.builder()
                    .stream(0).attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3)
                    .build();
            long stride = layout.strideOf(0);
            MemorySegment data = arena.allocate(stride * count);
            positionStream.transcodeInto(layout, data, 0, stride, 0, count);

            return computeFromSegment(data, count, stride);
        }
    }

    /**
     * Computes the AABB directly from a memory segment of F32x3 positions.
     *
     * @param data         segment containing position data
     * @param elementCount number of positions
     * @param stride       bytes between consecutive positions
     * @return the axis-aligned bounding box
     */
    public static AABB computeFromSegment(MemorySegment data, long elementCount, long stride) {
        if (elementCount == 0) {
            return new AABB(new Vec3(0, 0, 0), new Vec3(0, 0, 0));
        }

        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

        for (long i = 0; i < elementCount; i++) {
            long o = i * stride;
            float x = data.get(JAVA_FLOAT_UNALIGNED, o);
            float y = data.get(JAVA_FLOAT_UNALIGNED, o + 4);
            float z = data.get(JAVA_FLOAT_UNALIGNED, o + 8);
            minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
        }

        return new AABB(new Vec3(minX, minY, minZ), new Vec3(maxX, maxY, maxZ));
    }

    /**
     * Computes the AABB for a window of elements within a position stream.
     *
     * @param positionStream the stream
     * @param firstElement   first element index
     * @param elementCount   number of elements
     * @return the axis-aligned bounding box for the window
     */
    public static AABB computeWindow(AttributeStream positionStream,
                                     long firstElement, long elementCount) {
        if (!positionStream.isHostReadable()) {
            throw new IllegalStateException("Position stream must be host-readable for bounds computation");
        }
        if (elementCount == 0) {
            return new AABB(new Vec3(0, 0, 0), new Vec3(0, 0, 0));
        }

        try (Arena arena = Arena.ofConfined()) {
            MeshLayout layout = MeshLayout.builder()
                    .stream(0).attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3)
                    .build();
            long stride = layout.strideOf(0);
            MemorySegment data = arena.allocate(stride * elementCount);
            positionStream.transcodeInto(layout, data, 0, stride, firstElement, elementCount);

            return computeFromSegment(data, elementCount, stride);
        }
    }
}
