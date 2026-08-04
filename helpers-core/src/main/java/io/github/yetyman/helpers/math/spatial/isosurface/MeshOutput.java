package io.github.yetyman.helpers.math.spatial.isosurface;

import io.github.yetyman.helpers.math.Vec3;
import io.github.yetyman.vulkan.buffers.BufferWritable;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Output mesh from isosurface extraction (marching cubes, surface nets, etc.).
 * Contains vertices (Vec3 positions) and triangle indices.
 * Implements BufferWritable for direct GPU upload.
 */
public class MeshOutput implements BufferWritable {

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

    /**
     * Byte size: vertices (12 bytes each) + indices (4 bytes each).
     */
    @Override
    public int byteSize() {
        return vertices.size() * 12 + indices.size() * 4;
    }

    /**
     * Writes vertices then indices sequentially.
     */
    @Override
    public void writeTo(ByteBuffer buf) {
        for (Vec3 v : vertices) {
            buf.putFloat(v.x); buf.putFloat(v.y); buf.putFloat(v.z);
        }
        for (int idx : indices) {
            buf.putInt(idx);
        }
    }

    @Override
    public void readFrom(ByteBuffer buf) {
        throw new UnsupportedOperationException();
    }

    /** Byte size of just the vertex data. */
    public int vertexByteSize() { return vertices.size() * 12; }

    /** Byte size of just the index data. */
    public int indexByteSize() { return indices.size() * 4; }

    /** Writes only vertices to the buffer. */
    public void writeVertices(ByteBuffer buf) {
        for (Vec3 v : vertices) { buf.putFloat(v.x); buf.putFloat(v.y); buf.putFloat(v.z); }
    }

    /** Writes only indices to the buffer. */
    public void writeIndices(ByteBuffer buf) {
        for (int idx : indices) buf.putInt(idx);
    }
}
