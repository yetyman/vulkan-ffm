package io.github.yetyman.helpers.math.spatial.isosurface;

import io.github.yetyman.helpers.math.Vec3;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED;

/**
 * Output mesh from isosurface extraction (marching cubes, surface nets, etc.).
 * Contains vertices (Vec3 positions) and triangle indices.
 *
 * <p>Serialization is offset-explicit and writes into caller-provided native memory. Vertices and
 * indices are written independently so they can land in separate buffers, which is what a vertex
 * buffer plus index buffer pairing needs.
 *
 * <p>Planned: this type will become a {@code GeometrySource} implementation once the mesh module
 * exists, and its vertex storage will move off {@code List<Vec3>} onto a growable primitive or
 * native backing. See {@code plans/mesh/02-geometry-sources.md}.
 */
public class MeshOutput {

    private final List<Vec3> vertices = new ArrayList<>();
    private final List<Integer> indices = new ArrayList<>();

    public List<Vec3> vertices() { return vertices; }
    public List<Integer> indices() { return indices; }
    public int vertexCount() { return vertices.size(); }
    public int indexCount() { return indices.size(); }

    public int addVertex(Vec3 v) {
        int idx = vertices.size();
        vertices.add(v);
        return idx;
    }

    public int addVertex(float x, float y, float z) {
        return addVertex(new Vec3(x, y, z));
    }

    public void addTriangle(int a, int b, int c) {
        indices.add(a);
        indices.add(b);
        indices.add(c);
    }

    public void clear() {
        vertices.clear();
        indices.clear();
    }

    /** @return byte size of just the vertex block (12 bytes per vertex). */
    public int vertexByteSize() { return vertices.size() * 12; }

    /** @return byte size of just the index block (4 bytes per index). */
    public int indexByteSize() { return indices.size() * 4; }

    /** @return byte size of vertices followed by indices. */
    public int gpuByteSize() { return vertexByteSize() + indexByteSize(); }

    /** Writes only the vertices into {@code dst} starting at {@code offset}, packed as 3 floats each. */
    public void writeVertices(MemorySegment dst, long offset) {
        long o = offset;
        for (Vec3 v : vertices) {
            dst.set(JAVA_FLOAT_UNALIGNED, o, v.x);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 4, v.y);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 8, v.z);
            o += 12;
        }
    }

    /**
     * Writes only the vertices into {@code dst} starting at {@code offset}, advancing
     * {@code stride} bytes per vertex. Used to interleave positions into a packed vertex buffer.
     */
    public void writeVertices(MemorySegment dst, long offset, long stride) {
        long o = offset;
        for (Vec3 v : vertices) {
            dst.set(JAVA_FLOAT_UNALIGNED, o, v.x);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 4, v.y);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 8, v.z);
            o += stride;
        }
    }

    /** Writes only the 32-bit indices into {@code dst} starting at {@code offset}. */
    public void writeIndices(MemorySegment dst, long offset) {
        writeIndices(dst, offset, 0);
    }

    /**
     * Writes the 32-bit indices into {@code dst} starting at {@code offset}, adding
     * {@code vertexBaseOffset} to each index. The base offset is what makes shared global vertex
     * pools work, where a mesh's vertices do not start at index zero.
     */
    public void writeIndices(MemorySegment dst, long offset, int vertexBaseOffset) {
        long o = offset;
        for (int idx : indices) {
            dst.set(JAVA_INT_UNALIGNED, o, idx + vertexBaseOffset);
            o += 4;
        }
    }

    /** Writes vertices followed by indices into {@code dst} starting at {@code offset}. */
    public void writeTo(MemorySegment dst, long offset) {
        writeVertices(dst, offset);
        writeIndices(dst, offset + vertexByteSize());
    }
}
