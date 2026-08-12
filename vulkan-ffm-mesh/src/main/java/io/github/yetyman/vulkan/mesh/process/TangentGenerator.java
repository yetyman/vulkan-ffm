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
 * Generates per-vertex tangent vectors using the MikkTSpace algorithm (simplified).
 *
 * <p>Given a {@link GeometrySource} with positions, normals, and texture coordinates, this
 * produces an {@link AttributeStream} of F32x4 tangents (xyz = tangent direction, w = handedness
 * sign for bitangent reconstruction).</p>
 *
 * <p>Requirements:</p>
 * <ul>
 *   <li>{@link AttributeSemantic#POSITION} (F32x3, host-readable)</li>
 *   <li>{@link AttributeSemantic#NORMAL} (F32x3, host-readable)</li>
 *   <li>{@link AttributeSemantic#TEXCOORD TEXCOORD(0)} (F32x2, host-readable)</li>
 *   <li>Source must be indexed with a triangle-list topology</li>
 * </ul>
 *
 * <p>This implementation computes tangents per-face and accumulates them per-vertex (same approach
 * as normal generation), then orthogonalizes against the vertex normal using Gram-Schmidt.
 * For production use with proper MikkTSpace compliance, use the optimized implementation in
 * the {@code vulkan-ffm-mesh-processing} sibling module.</p>
 */
public final class TangentGenerator {

    private TangentGenerator() {}

    /**
     * Generates tangent vectors (F32x4: xyz = tangent, w = handedness) for the given source.
     *
     * @param source the geometry (must have positions, normals, texcoords, and be indexed)
     * @param arena  arena to allocate the result in
     * @return an AttributeStream carrying F32x4 tangents
     */
    public static AttributeStream generate(GeometrySource source, Arena arena) {
        if (!source.available().contains(AttributeSemantic.POSITION))
            throw new IllegalArgumentException("Source must have POSITION");
        if (!source.available().contains(AttributeSemantic.NORMAL))
            throw new IllegalArgumentException("Source must have NORMAL");
        if (!source.available().contains(AttributeSemantic.TEXCOORD(0)))
            throw new IllegalArgumentException("Source must have TEXCOORD(0)");
        if (source.indices().isEmpty())
            throw new IllegalArgumentException("Source must be indexed");

        IndexStream idxStream = source.indices().get();
        if (!idxStream.isHostReadable())
            throw new IllegalStateException("Index stream must be host-readable");

        long vertexCount = source.elementCount();
        long indexCount = idxStream.indexCount();

        // Read positions, normals, UVs
        MeshLayout readLayout = MeshLayout.builder()
                .stream(0)
                .attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3)
                .attribute(AttributeSemantic.NORMAL, AttributeFormat.F32x3)
                .attribute(AttributeSemantic.TEXCOORD(0), AttributeFormat.F32x2)
                .build();

        long posStride = 12L;
        long nrmStride = 12L;
        long uvStride = 8L;

        MemorySegment positions = arena.allocate(posStride * vertexCount);
        MemorySegment normals = arena.allocate(nrmStride * vertexCount);
        MemorySegment uvs = arena.allocate(uvStride * vertexCount);

        MeshLayout posLayout = MeshLayout.builder().stream(0).attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3).build();
        MeshLayout nrmLayout = MeshLayout.builder().stream(0).attribute(AttributeSemantic.NORMAL, AttributeFormat.F32x3).build();
        MeshLayout uvLayout = MeshLayout.builder().stream(0).attribute(AttributeSemantic.TEXCOORD(0), AttributeFormat.F32x2).build();

        source.stream(AttributeSemantic.POSITION).transcodeInto(posLayout, positions, 0, posStride, 0, vertexCount);
        source.stream(AttributeSemantic.NORMAL).transcodeInto(nrmLayout, normals, 0, nrmStride, 0, vertexCount);
        source.stream(AttributeSemantic.TEXCOORD(0)).transcodeInto(uvLayout, uvs, 0, uvStride, 0, vertexCount);

        // Read indices
        MemorySegment indices = arena.allocate(indexCount * 4L);
        idxStream.transcodeInto(IndexWidth.U32, 0, indices, 0, 0, indexCount);

        // Accumulate tangents and bitangents per vertex
        long tanStride = 12L;
        MemorySegment tangents = arena.allocate(tanStride * vertexCount);
        MemorySegment bitangents = arena.allocate(tanStride * vertexCount);
        tangents.fill((byte) 0);
        bitangents.fill((byte) 0);

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

            float u0 = uvs.get(JAVA_FLOAT_UNALIGNED, i0 * uvStride);
            float v0 = uvs.get(JAVA_FLOAT_UNALIGNED, i0 * uvStride + 4);
            float u1 = uvs.get(JAVA_FLOAT_UNALIGNED, i1 * uvStride);
            float v1 = uvs.get(JAVA_FLOAT_UNALIGNED, i1 * uvStride + 4);
            float u2 = uvs.get(JAVA_FLOAT_UNALIGNED, i2 * uvStride);
            float v2 = uvs.get(JAVA_FLOAT_UNALIGNED, i2 * uvStride + 4);

            float e1x = p1x - p0x, e1y = p1y - p0y, e1z = p1z - p0z;
            float e2x = p2x - p0x, e2y = p2y - p0y, e2z = p2z - p0z;
            float du1 = u1 - u0, dv1 = v1 - v0;
            float du2 = u2 - u0, dv2 = v2 - v0;

            float det = du1 * dv2 - du2 * dv1;
            float r = (Math.abs(det) > 1e-8f) ? 1.0f / det : 0.0f;

            float tx = (dv2 * e1x - dv1 * e2x) * r;
            float ty = (dv2 * e1y - dv1 * e2y) * r;
            float tz = (dv2 * e1z - dv1 * e2z) * r;

            float bx = (du1 * e2x - du2 * e1x) * r;
            float by = (du1 * e2y - du2 * e1y) * r;
            float bz = (du1 * e2z - du2 * e1z) * r;

            accum3(tangents, i0, tanStride, tx, ty, tz);
            accum3(tangents, i1, tanStride, tx, ty, tz);
            accum3(tangents, i2, tanStride, tx, ty, tz);
            accum3(bitangents, i0, tanStride, bx, by, bz);
            accum3(bitangents, i1, tanStride, bx, by, bz);
            accum3(bitangents, i2, tanStride, bx, by, bz);
        }

        // Orthogonalize and compute handedness -> F32x4 output
        long outStride = 16L; // F32x4
        MemorySegment result = arena.allocate(outStride * vertexCount);

        for (long v = 0; v < vertexCount; v++) {
            float nx = normals.get(JAVA_FLOAT_UNALIGNED, v * nrmStride);
            float ny = normals.get(JAVA_FLOAT_UNALIGNED, v * nrmStride + 4);
            float nz = normals.get(JAVA_FLOAT_UNALIGNED, v * nrmStride + 8);

            float tx = tangents.get(JAVA_FLOAT_UNALIGNED, v * tanStride);
            float ty = tangents.get(JAVA_FLOAT_UNALIGNED, v * tanStride + 4);
            float tz = tangents.get(JAVA_FLOAT_UNALIGNED, v * tanStride + 8);

            float bx = bitangents.get(JAVA_FLOAT_UNALIGNED, v * tanStride);
            float by = bitangents.get(JAVA_FLOAT_UNALIGNED, v * tanStride + 4);
            float bz = bitangents.get(JAVA_FLOAT_UNALIGNED, v * tanStride + 8);

            // Gram-Schmidt: t' = normalize(t - n * dot(n, t))
            float dot = nx * tx + ny * ty + nz * tz;
            float ox = tx - nx * dot;
            float oy = ty - ny * dot;
            float oz = tz - nz * dot;
            float len = (float) Math.sqrt(ox * ox + oy * oy + oz * oz);
            if (len > 1e-8f) {
                float inv = 1.0f / len;
                ox *= inv; oy *= inv; oz *= inv;
            } else {
                ox = 0; oy = 1; oz = 0;
            }

            // Handedness: sign of dot(cross(n, t), b)
            float cx = ny * tz - nz * ty;
            float cy = nz * tx - nx * tz;
            float cz = nx * ty - ny * tx;
            float w = (cx * bx + cy * by + cz * bz) < 0.0f ? -1.0f : 1.0f;

            long o = v * outStride;
            result.set(JAVA_FLOAT_UNALIGNED, o, ox);
            result.set(JAVA_FLOAT_UNALIGNED, o + 4, oy);
            result.set(JAVA_FLOAT_UNALIGNED, o + 8, oz);
            result.set(JAVA_FLOAT_UNALIGNED, o + 12, w);
        }

        return new SegmentAttributeStream(AttributeSemantic.TANGENT, AttributeFormat.F32x4,
                vertexCount, result);
    }

    private static void accum3(MemorySegment seg, int index, long stride,
                               float x, float y, float z) {
        long o = index * stride;
        seg.set(JAVA_FLOAT_UNALIGNED, o, seg.get(JAVA_FLOAT_UNALIGNED, o) + x);
        seg.set(JAVA_FLOAT_UNALIGNED, o + 4, seg.get(JAVA_FLOAT_UNALIGNED, o + 4) + y);
        seg.set(JAVA_FLOAT_UNALIGNED, o + 8, seg.get(JAVA_FLOAT_UNALIGNED, o + 8) + z);
    }
}
