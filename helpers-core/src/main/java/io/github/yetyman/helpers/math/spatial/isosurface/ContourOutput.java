package io.github.yetyman.helpers.math.spatial.isosurface;

import io.github.yetyman.helpers.math.Vec2;
import io.github.yetyman.vulkan.buffers.BufferWritable;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Output contour from 2D isosurface extraction (marching squares).
 * Contains line segment endpoints as Vec2 pairs.
 */
public class ContourOutput implements BufferWritable {

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

    @Override
    public int byteSize() { return vertices.size() * 8 + segments.size() * 8; }

    @Override
    public void writeTo(ByteBuffer buf) {
        for (Vec2 v : vertices) { buf.putFloat(v.x); buf.putFloat(v.y); }
        for (int[] seg : segments) { buf.putInt(seg[0]); buf.putInt(seg[1]); }
    }

    @Override
    public void readFrom(ByteBuffer buf) { throw new UnsupportedOperationException(); }
}
