package io.github.yetyman.vulkan.sample.mesh;

import io.github.yetyman.helpers.math.Vec3;
import io.github.yetyman.helpers.math.geometry.AABB;
import io.github.yetyman.vulkan.mesh.AttributeFormat;
import io.github.yetyman.vulkan.mesh.AttributeSemantic;
import io.github.yetyman.vulkan.mesh.IndexWidth;
import io.github.yetyman.vulkan.mesh.MeshLayout;
import io.github.yetyman.vulkan.mesh.PrimitiveTopology;
import io.github.yetyman.vulkan.mesh.source.GeometrySource;
import io.github.yetyman.vulkan.mesh.source.SegmentGeometrySource;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED;

/**
 * Generates 7-segment-display style digit meshes (0-9) for LOD visualization.
 * Each digit is built from rectangular box segments, producing clean geometry
 * with no concavity issues.
 *
 * <p>The digits are roughly 1.0 units wide and 2.0 units tall, centered at the origin.
 * Each segment has 0.15 unit depth (Z axis) for a solid 3D appearance.
 */
public final class NumberMeshSource {

    private NumberMeshSource() {}

    // Segment thickness (width of a horizontal bar / height of a vertical bar portion)
    private static final float T = 0.15f;
    // Half-width of the digit
    private static final float HW = 0.45f;
    // Half-height of the digit
    private static final float HH = 0.9f;
    // Depth of extrusion
    private static final float DEPTH = 0.15f;

    /**
     * 7-segment layout:
     * <pre>
     *   --0--
     *  |     |
     *  5     1
     *  |     |
     *   --6--
     *  |     |
     *  4     2
     *  |     |
     *   --3--
     * </pre>
     * Each segment is a box defined by (minX, minY, maxX, maxY).
     */
    private static final float[][] SEGMENTS = {
            // 0: top horizontal
            {-HW + T, HH - T, HW - T, HH},
            // 1: upper-right vertical
            {HW - T, T * 0.5f, HW, HH - T},
            // 2: lower-right vertical
            {HW - T, -HH + T, HW, -T * 0.5f},
            // 3: bottom horizontal
            {-HW + T, -HH, HW - T, -HH + T},
            // 4: lower-left vertical
            {-HW, -HH + T, -HW + T, -T * 0.5f},
            // 5: upper-left vertical
            {-HW, T * 0.5f, -HW + T, HH - T},
            // 6: middle horizontal
            {-HW + T, -T * 0.5f, HW - T, T * 0.5f},
    };

    /**
     * Which segments are active for each digit (0-9).
     * Indexed by digit, contains segment indices.
     */
    private static final int[][] DIGIT_SEGMENTS = {
            {0, 1, 2, 3, 4, 5},       // 0
            {1, 2},                     // 1
            {0, 1, 6, 4, 3},           // 2
            {0, 1, 6, 2, 3},           // 3
            {5, 6, 1, 2},             // 4
            {0, 5, 6, 2, 3},           // 5
            {0, 5, 4, 3, 2, 6},        // 6
            {0, 1, 2},                 // 7
            {0, 1, 2, 3, 4, 5, 6},    // 8
            {0, 1, 2, 3, 5, 6},        // 9
    };

    /**
     * Creates a geometry source for the given digit (0-9).
     *
     * @param digit digit 0-9
     * @param arena arena to allocate backing memory in
     * @return geometry source for the digit mesh
     */
    public static GeometrySource create(int digit, Arena arena) {
        if (digit < 0 || digit > 9) throw new IllegalArgumentException("digit must be 0-9, got " + digit);
        return buildDigitMesh(DIGIT_SEGMENTS[digit], arena);
    }

    private static GeometrySource buildDigitMesh(int[] activeSegments, Arena arena) {
        int segCount = activeSegments.length;
        // Each segment is a box: 24 verts (4 per face x 6 faces), 36 indices
        int vertsPerSeg = 24;
        int indicesPerSeg = 36;
        int totalVerts = segCount * vertsPerSeg;
        int totalIndices = segCount * indicesPerSeg;

        MeshLayout layout = MeshLayout.builder()
                .stream(0)
                .attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3)
                .attribute(AttributeSemantic.NORMAL, AttributeFormat.F32x3)
                .attribute(AttributeSemantic.TEXCOORD(0), AttributeFormat.F32x2)
                .build();

        long stride = layout.strideOf(0); // 32 bytes
        MemorySegment verts = arena.allocate(stride * totalVerts);
        MemorySegment idxs = arena.allocate((long) totalIndices * 4);

        int vi = 0;
        int ii = 0;
        float halfDepth = DEPTH * 0.5f;

        for (int segIdx : activeSegments) {
            float[] seg = SEGMENTS[segIdx];
            float minX = seg[0], minY = seg[1], maxX = seg[2], maxY = seg[3];

            // Build a box from (minX, minY, -halfDepth) to (maxX, maxY, halfDepth)
            int baseVert = vi;
            vi = writeBoxVerts(verts, stride, vi, minX, minY, -halfDepth, maxX, maxY, halfDepth);
            ii = writeBoxIndices(idxs, ii, baseVert);
        }

        return SegmentGeometrySource.builder()
                .layout(layout)
                .elementCount(totalVerts)
                .topology(PrimitiveTopology.TRIANGLE_LIST)
                .bounds(new AABB(
                        new Vec3(-HW, -HH, -halfDepth),
                        new Vec3(HW, HH, halfDepth)))
                .streamData(0, verts)
                .indices(IndexWidth.U32, totalIndices, idxs)
                .build();
    }

    /**
     * Writes 24 vertices (6 faces x 4 verts) for a box into the vertex buffer.
     * Returns the new vertex index after writing.
     */
    private static int writeBoxVerts(MemorySegment verts, long stride, int vi,
                                     float minX, float minY, float minZ,
                                     float maxX, float maxY, float maxZ) {
        // Face definitions: {normal, 4 corner positions}
        float[][][] faces = {
                // +Z face (front)
                {{0, 0, 1}, {minX, minY, maxZ}, {maxX, minY, maxZ}, {maxX, maxY, maxZ}, {minX, maxY, maxZ}},
                // -Z face (back)
                {{0, 0, -1}, {maxX, minY, minZ}, {minX, minY, minZ}, {minX, maxY, minZ}, {maxX, maxY, minZ}},
                // +Y face (top)
                {{0, 1, 0}, {minX, maxY, maxZ}, {maxX, maxY, maxZ}, {maxX, maxY, minZ}, {minX, maxY, minZ}},
                // -Y face (bottom)
                {{0, -1, 0}, {minX, minY, minZ}, {maxX, minY, minZ}, {maxX, minY, maxZ}, {minX, minY, maxZ}},
                // +X face (right)
                {{1, 0, 0}, {maxX, minY, maxZ}, {maxX, minY, minZ}, {maxX, maxY, minZ}, {maxX, maxY, maxZ}},
                // -X face (left)
                {{-1, 0, 0}, {minX, minY, minZ}, {minX, minY, maxZ}, {minX, maxY, maxZ}, {minX, maxY, minZ}},
        };

        float[][] uvs = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};

        for (float[][] face : faces) {
            float nx = face[0][0], ny = face[0][1], nz = face[0][2];
            for (int c = 0; c < 4; c++) {
                long o = (long) vi * stride;
                verts.set(JAVA_FLOAT_UNALIGNED, o, face[1 + c][0]);
                verts.set(JAVA_FLOAT_UNALIGNED, o + 4, face[1 + c][1]);
                verts.set(JAVA_FLOAT_UNALIGNED, o + 8, face[1 + c][2]);
                verts.set(JAVA_FLOAT_UNALIGNED, o + 12, nx);
                verts.set(JAVA_FLOAT_UNALIGNED, o + 16, ny);
                verts.set(JAVA_FLOAT_UNALIGNED, o + 20, nz);
                verts.set(JAVA_FLOAT_UNALIGNED, o + 24, uvs[c][0]);
                verts.set(JAVA_FLOAT_UNALIGNED, o + 28, uvs[c][1]);
                vi++;
            }
        }
        return vi;
    }

    /**
     * Writes 36 indices (6 faces x 2 triangles x 3) for a box.
     * Returns the new index offset after writing.
     */
    private static int writeBoxIndices(MemorySegment idxs, int ii, int baseVert) {
        for (int face = 0; face < 6; face++) {
            int base = baseVert + face * 4;
            idxs.set(JAVA_INT_UNALIGNED, (long) ii * 4, base);
            idxs.set(JAVA_INT_UNALIGNED, (long) (ii + 1) * 4, base + 1);
            idxs.set(JAVA_INT_UNALIGNED, (long) (ii + 2) * 4, base + 2);
            idxs.set(JAVA_INT_UNALIGNED, (long) (ii + 3) * 4, base);
            idxs.set(JAVA_INT_UNALIGNED, (long) (ii + 4) * 4, base + 2);
            idxs.set(JAVA_INT_UNALIGNED, (long) (ii + 5) * 4, base + 3);
            ii += 6;
        }
        return ii;
    }
}
