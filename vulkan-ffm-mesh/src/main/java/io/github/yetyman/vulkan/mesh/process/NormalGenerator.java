package io.github.yetyman.vulkan.mesh.process;

import io.github.yetyman.vulkan.mesh.AttributeFormat;
import io.github.yetyman.vulkan.mesh.AttributeSemantic;
import io.github.yetyman.vulkan.mesh.IndexWidth;
import io.github.yetyman.vulkan.mesh.MeshLayout;
import io.github.yetyman.vulkan.mesh.source.AttributeStream;
import io.github.yetyman.vulkan.mesh.source.GeometrySource;
import io.github.yetyman.vulkan.mesh.source.IndexStream;
import io.github.yetyman.vulkan.mesh.source.SegmentAttributeStream;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED;

/**
 * Generates per-vertex normals from triangle geometry by area-weighted face normal averaging.
 *
 * <p>Given a {@link GeometrySource} with positions and indices, this produces an
 * {@link AttributeStream} of F32x3 normals that can be attached to the source or used
 * independently. The generated normals are smooth (area-weighted average of adjacent face
 * normals), which is the correct default for most organic geometry.</p>
 *
 * <p>Requirements:</p>
 * <ul>
 *   <li>Source must have {@link AttributeSemantic#POSITION} (F32x3, host-readable)</li>
 *   <li>Source must be indexed with a triangle-list topology</li>
 * </ul>
 *
 * <p>This is a small, universal, dependency-free processor. Optimized parallel or GPU-accelerated
 * normal generation belongs in the {@code vulkan-ffm-mesh-processing} sibling module.</p>
 */
public final class NormalGenerator {

    private NormalGenerator() {}

    /**
     * Generates smooth normals for the given geometry source.
     *
     * @param source the geometry (must have positions and indices, triangle-list topology)
     * @param arena  arena to allocate the result normal data in
     * @return an AttributeStream carrying the generated normals
     */
    public static AttributeStream generate(GeometrySource source, Arena arena) {
        if (!source.available().contains(AttributeSemantic.POSITION)) {
            throw new IllegalArgumentException("Source must have POSITION attribute");
        }
        if (source.indices().isEmpty()) {
            throw new IllegalArgumentException("Source must be indexed");
        }

        AttributeStream posStream = source.stream(AttributeSemantic.POSITION);
        if (!posStream.isHostReadable()) {
            throw new IllegalStateException("POSITION stream must be host-readable");
        }
        IndexStream idxStream = source.indices().get();
        if (!idxStream.isHostReadable()) {
            throw new IllegalStateException("Index stream must be host-readable");
        }

        long vertexCount = source.elementCount();
        long indexCount = idxStream.indexCount();

        // Read positions into a flat F32x3 buffer
        MeshLayout posLayout = MeshLayout.builder()
                .stream(0).attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3)
                .build();
        long posStride = posLayout.strideOf(0); // 12
        MemorySegment positions = arena.allocate(posStride * vertexCount);
        posStream.transcodeInto(posLayout, positions, 0, posStride, 0, vertexCount);

        // Read indices as U32
        MemorySegment indices = arena.allocate(indexCount * 4L);
        idxStream.transcodeInto(IndexWidth.U32, 0, indices, 0, 0, indexCount);

        // Allocate normals (initialized to zero)
        long normalStride = 12; // F32x3
        MemorySegment normals = arena.allocate(normalStride * vertexCount);
        normals.fill((byte) 0);

        // Accumulate area-weighted face normals
        long triangleCount = indexCount / 3;
        for (long t = 0; t < triangleCount; t++) {
            long base = t * 3;
            int i0 = indices.get(JAVA_INT_UNALIGNED, base * 4);
            int i1 = indices.get(JAVA_INT_UNALIGNED, (base + 1) * 4);
            int i2 = indices.get(JAVA_INT_UNALIGNED, (base + 2) * 4);

            float p0x = positions.get(JAVA_FLOAT_UNALIGNED, i0 * posStride);
            float p0y = positions.get(JAVA_FLOAT_UNALIGNED, i0 * posStride + 4);
            float p0z = positions.get(JAVA_FLOAT_UNALIGNED, i0 * posStride + 8);
            float p1x = positions.get(JAVA_FLOAT_UNALIGNED, i1 * posStride);
            float p1y = positions.get(JAVA_FLOAT_UNALIGNED, i1 * posStride + 4);
            float p1z = positions.get(JAVA_FLOAT_UNALIGNED, i1 * posStride + 8);
            float p2x = positions.get(JAVA_FLOAT_UNALIGNED, i2 * posStride);
            float p2y = positions.get(JAVA_FLOAT_UNALIGNED, i2 * posStride + 4);
            float p2z = positions.get(JAVA_FLOAT_UNALIGNED, i2 * posStride + 8);

            // Cross product of edges (area-weighted normal, not normalized)
            float e1x = p1x - p0x, e1y = p1y - p0y, e1z = p1z - p0z;
            float e2x = p2x - p0x, e2y = p2y - p0y, e2z = p2z - p0z;
            float nx = e1y * e2z - e1z * e2y;
            float ny = e1z * e2x - e1x * e2z;
            float nz = e1x * e2y - e1y * e2x;

            // Accumulate into each vertex of the triangle
            accumulate(normals, i0, normalStride, nx, ny, nz);
            accumulate(normals, i1, normalStride, nx, ny, nz);
            accumulate(normals, i2, normalStride, nx, ny, nz);
        }

        // Normalize all accumulated normals
        for (long v = 0; v < vertexCount; v++) {
            long o = v * normalStride;
            float nx = normals.get(JAVA_FLOAT_UNALIGNED, o);
            float ny = normals.get(JAVA_FLOAT_UNALIGNED, o + 4);
            float nz = normals.get(JAVA_FLOAT_UNALIGNED, o + 8);
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len > 1e-8f) {
                float inv = 1.0f / len;
                normals.set(JAVA_FLOAT_UNALIGNED, o, nx * inv);
                normals.set(JAVA_FLOAT_UNALIGNED, o + 4, ny * inv);
                normals.set(JAVA_FLOAT_UNALIGNED, o + 8, nz * inv);
            } else {
                // Degenerate: default to up
                normals.set(JAVA_FLOAT_UNALIGNED, o, 0.0f);
                normals.set(JAVA_FLOAT_UNALIGNED, o + 4, 1.0f);
                normals.set(JAVA_FLOAT_UNALIGNED, o + 8, 0.0f);
            }
        }

        return new SegmentAttributeStream(AttributeSemantic.NORMAL, AttributeFormat.F32x3,
                vertexCount, normals);
    }

    private static void accumulate(MemorySegment normals, int vertexIndex, long stride,
                                   float nx, float ny, float nz) {
        long o = vertexIndex * stride;
        normals.set(JAVA_FLOAT_UNALIGNED, o, normals.get(JAVA_FLOAT_UNALIGNED, o) + nx);
        normals.set(JAVA_FLOAT_UNALIGNED, o + 4, normals.get(JAVA_FLOAT_UNALIGNED, o + 4) + ny);
        normals.set(JAVA_FLOAT_UNALIGNED, o + 8, normals.get(JAVA_FLOAT_UNALIGNED, o + 8) + nz);
    }
}
