package io.github.yetyman.helpers.math.spatial.isosurface;

import io.github.yetyman.helpers.math.Vec2;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED;

/**
 * Output contour from 2D isosurface extraction (marching squares).
 * Contains line segment endpoints as Vec2 pairs.
 *
 * <p>Serialization is offset-explicit and writes into caller-provided native memory. Vertices and
 * segments can be written independently so they can land in separate buffers, which is what a
 * vertex buffer plus index buffer pairing needs.
 */
public class ContourOutput {

    private final List<Vec2> vertices = new ArrayList<>();
    private final List<int[]> segments = new ArrayList<>(); // pairs of vertex indices

    public List<Vec2> vertices() { return vertices; }
    public List<int[]> segments() { return segments; }

    public int addVertex(float x, float y) {
        int idx = vertices.size();
        vertices.add(new Vec2(x, y));
        return idx;
    }

    public void addSegment(int a, int b) {
        segments.add(new int[]{a, b});
    }

    public void clear() { vertices.clear(); segments.clear(); }

    /** @return byte size of the vertex block (8 bytes per vertex). */
    public int vertexByteSize() { return vertices.size() * 8; }

    /** @return byte size of the segment index block (8 bytes per segment). */
    public int segmentByteSize() { return segments.size() * 8; }

    /** @return byte size of vertices followed by segment indices. */
    public int gpuByteSize() { return vertexByteSize() + segmentByteSize(); }

    /** Writes only the vertices into {@code dst} starting at {@code offset}. */
    public void writeVertices(MemorySegment dst, long offset) {
        long o = offset;
        for (Vec2 v : vertices) {
            dst.set(JAVA_FLOAT_UNALIGNED, o, v.x);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 4, v.y);
            o += 8;
        }
    }

    /** Writes only the segment indices into {@code dst} starting at {@code offset}. */
    public void writeSegments(MemorySegment dst, long offset) {
        long o = offset;
        for (int[] seg : segments) {
            dst.set(JAVA_INT_UNALIGNED, o, seg[0]);
            dst.set(JAVA_INT_UNALIGNED, o + 4, seg[1]);
            o += 8;
        }
    }

    /** Writes vertices followed by segment indices into {@code dst} starting at {@code offset}. */
    public void writeTo(MemorySegment dst, long offset) {
        writeVertices(dst, offset);
        writeSegments(dst, offset + vertexByteSize());
    }
}
