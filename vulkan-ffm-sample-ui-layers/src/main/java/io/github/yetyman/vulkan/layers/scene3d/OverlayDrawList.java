package io.github.yetyman.vulkan.layers.scene3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Accumulates 3D overlay draw primitives (lines, wire boxes, spheres, grids, axes) for a single
 * frame into flat vertex lists, split by topology (line vs triangle) and DepthMode. Cleared at
 * the start of each frame by Scene3DOverlayLayer.update().
 *
 * <p>Internally uses flat float arrays to avoid per-vertex object allocation. Each vertex
 * is 7 floats: x, y, z, r, g, b, a. The arrays grow as needed and retain capacity across
 * frames (clear() resets count but not capacity).
 */
public class OverlayDrawList {
    // Flat float arrays: 7 floats per vertex (x, y, z, r, g, b, a)
    private static final int FLOATS_PER_VERTEX = 7;
    private static final int INITIAL_CAPACITY = 4096 * FLOATS_PER_VERTEX; // room for 4096 verts

    private float[] depthTestedLineData = new float[INITIAL_CAPACITY];
    private float[] onTopLineData = new float[INITIAL_CAPACITY];
    private float[] depthTestedTriData = new float[INITIAL_CAPACITY];
    private float[] onTopTriData = new float[INITIAL_CAPACITY];

    private int depthTestedLineCount = 0; // vertex count
    private int onTopLineCount = 0;
    private int depthTestedTriCount = 0;
    private int onTopTriCount = 0;

    /** Clears all accumulated primitives. Called once per frame before draw calls. */
    public void clear() {
        depthTestedLineCount = 0;
        onTopLineCount = 0;
        depthTestedTriCount = 0;
        onTopTriCount = 0;
    }

    /** @return true if nothing has been accumulated this frame. */
    public boolean isEmpty() {
        return depthTestedLineCount == 0 && onTopLineCount == 0
            && depthTestedTriCount == 0 && onTopTriCount == 0;
    }

    // --- Vertex counts ---
    public int depthTestedLineVertexCount() { return depthTestedLineCount; }
    public int onTopLineVertexCount() { return onTopLineCount; }
    public int depthTestedTriVertexCount() { return depthTestedTriCount; }
    public int onTopTriVertexCount() { return onTopTriCount; }

    // --- Raw float data access (for renderer upload) ---
    public float[] depthTestedLineData() { return depthTestedLineData; }
    public float[] onTopLineData() { return onTopLineData; }
    public float[] depthTestedTriData() { return depthTestedTriData; }
    public float[] onTopTriData() { return onTopTriData; }

    // --- Legacy List accessors (for compatibility with existing code that reads vertices) ---
    public List<OverlayVertex> depthTestedLines() { return toVertexList(depthTestedLineData, depthTestedLineCount); }
    public List<OverlayVertex> onTopLines() { return toVertexList(onTopLineData, onTopLineCount); }
    public List<OverlayVertex> depthTestedTris() { return toVertexList(depthTestedTriData, depthTestedTriCount); }
    public List<OverlayVertex> onTopTris() { return toVertexList(onTopTriData, onTopTriCount); }

    private List<OverlayVertex> toVertexList(float[] data, int count) {
        List<OverlayVertex> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int off = i * FLOATS_PER_VERTEX;
            list.add(new OverlayVertex(data[off], data[off+1], data[off+2],
                    data[off+3], data[off+4], data[off+5], data[off+6]));
        }
        return list;
    }

    /** Appends a single line segment from `from` to `to`. */
    public void addLine(float[] from, float[] to, float[] color, DepthMode mode) {
        if (mode == DepthMode.DEPTH_TESTED) {
            depthTestedLineData = ensureCapacity(depthTestedLineData, depthTestedLineCount + 2);
            depthTestedLineCount = appendVertex(depthTestedLineData, depthTestedLineCount, from, color);
            depthTestedLineCount = appendVertex(depthTestedLineData, depthTestedLineCount, to, color);
        } else {
            onTopLineData = ensureCapacity(onTopLineData, onTopLineCount + 2);
            onTopLineCount = appendVertex(onTopLineData, onTopLineCount, from, color);
            onTopLineCount = appendVertex(onTopLineData, onTopLineCount, to, color);
        }
    }

    /** Appends the 12 edges of an axis-aligned wire box spanning [min, max]. */
    public void addWireBox(float[] min, float[] max, float[] color, DepthMode mode) {
        float[][] corners = {
            {min[0], min[1], min[2]}, {max[0], min[1], min[2]},
            {max[0], max[1], min[2]}, {min[0], max[1], min[2]},
            {min[0], min[1], max[2]}, {max[0], min[1], max[2]},
            {max[0], max[1], max[2]}, {min[0], max[1], max[2]}
        };
        int[][] edges = {
            {0, 1}, {1, 2}, {2, 3}, {3, 0},
            {4, 5}, {5, 6}, {6, 7}, {7, 4},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };
        for (int[] edge : edges) {
            addLine(corners[edge[0]], corners[edge[1]], color, mode);
        }
    }

    /** Appends a wire sphere built from three orthogonal circles (XY, XZ, YZ planes). */
    public void addWireSphere(float[] center, float radius, float[] color, int segments, DepthMode mode) {
        addCircle(center, radius, segments, color, 0, mode);
        addCircle(center, radius, segments, color, 1, mode);
        addCircle(center, radius, segments, color, 2, mode);
    }

    private final float[] circA = new float[3];
    private final float[] circB = new float[3];

    private void addCircle(float[] center, float radius, int segments, float[] color, int plane, DepthMode mode) {
        for (int i = 0; i < segments; i++) {
            float a0 = (float) (2.0 * Math.PI * i / segments);
            float a1 = (float) (2.0 * Math.PI * (i + 1) / segments);
            circlePoint(center, radius, a0, plane, circA);
            circlePoint(center, radius, a1, plane, circB);
            addLine(circA, circB, color, mode);
        }
    }

    private void circlePoint(float[] center, float radius, float angle, int plane, float[] out) {
        float c = (float) Math.cos(angle) * radius;
        float s = (float) Math.sin(angle) * radius;
        switch (plane) {
            case 0 -> { out[0] = center[0] + c; out[1] = center[1] + s; out[2] = center[2]; }
            case 1 -> { out[0] = center[0] + c; out[1] = center[1]; out[2] = center[2] + s; }
            default -> { out[0] = center[0]; out[1] = center[1] + c; out[2] = center[2] + s; }
        }
    }

    /** Appends a ray as a single line of the given length from origin along direction. */
    public void addRay(float[] origin, float[] direction, float length, float[] color) {
        float[] end = {
            origin[0] + direction[0] * length,
            origin[1] + direction[1] * length,
            origin[2] + direction[2] * length
        };
        addLine(origin, end, color, DepthMode.ALWAYS_ON_TOP);
    }

    /** Appends an arrow: a line shaft plus a simple triangle-pair head at `to`. */
    public void addArrow(float[] from, float[] to, float[] color, float headSize) {
        addLine(from, to, color, DepthMode.ALWAYS_ON_TOP);

        float[] dir = normalize(sub(to, from));
        float[] arbitrary = Math.abs(dir[1]) < 0.99f ? new float[]{0, 1, 0} : new float[]{1, 0, 0};
        float[] side = normalize(cross(dir, arbitrary));

        float[] backCenter = {
            to[0] - dir[0] * headSize, to[1] - dir[1] * headSize, to[2] - dir[2] * headSize
        };
        float[] backLeft = {
            backCenter[0] + side[0] * headSize * 0.5f,
            backCenter[1] + side[1] * headSize * 0.5f,
            backCenter[2] + side[2] * headSize * 0.5f
        };
        float[] backRight = {
            backCenter[0] - side[0] * headSize * 0.5f,
            backCenter[1] - side[1] * headSize * 0.5f,
            backCenter[2] - side[2] * headSize * 0.5f
        };

        appendTriVertex(to, color, DepthMode.ALWAYS_ON_TOP);
        appendTriVertex(backLeft, color, DepthMode.ALWAYS_ON_TOP);
        appendTriVertex(backRight, color, DepthMode.ALWAYS_ON_TOP);
    }

    /** Appends a wireframe frustum from an inverse view-projection matrix. */
    public void addFrustum(float[] inverseViewProj, float[] color) {
        float[][] ndcCorners = {
            {-1, -1, 0}, {1, -1, 0}, {1, 1, 0}, {-1, 1, 0},
            {-1, -1, 1}, {1, -1, 1}, {1, 1, 1}, {-1, 1, 1}
        };
        float[][] worldCorners = new float[8][];
        for (int i = 0; i < 8; i++) {
            worldCorners[i] = unprojectPoint(inverseViewProj, ndcCorners[i]);
        }
        int[][] edges = {
            {0, 1}, {1, 2}, {2, 3}, {3, 0},
            {4, 5}, {5, 6}, {6, 7}, {7, 4},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };
        for (int[] edge : edges) {
            addLine(worldCorners[edge[0]], worldCorners[edge[1]], color, DepthMode.ALWAYS_ON_TOP);
        }
    }

    private float[] unprojectPoint(float[] m, float[] ndc) {
        float x = ndc[0], y = ndc[1], z = ndc[2];
        float w = m[3] * x + m[7] * y + m[11] * z + m[15];
        float wx = m[0] * x + m[4] * y + m[8] * z + m[12];
        float wy = m[1] * x + m[5] * y + m[9] * z + m[13];
        float wz = m[2] * x + m[6] * y + m[10] * z + m[14];
        if (Math.abs(w) < 1e-8f) w = 1e-8f;
        return new float[]{wx / w, wy / w, wz / w};
    }

    /** Appends a flat grid of lines on the XZ plane centered at `center`. */
    public void addGrid(float[] center, float size, int divisions, float[] color) {
        float half = size * 0.5f;
        float step = size / divisions;
        float[] a = new float[3];
        float[] b = new float[3];
        for (int i = 0; i <= divisions; i++) {
            float offset = -half + i * step;
            a[0] = center[0] + offset; a[1] = center[1]; a[2] = center[2] - half;
            b[0] = center[0] + offset; b[1] = center[1]; b[2] = center[2] + half;
            addLine(a, b, color, DepthMode.ALWAYS_ON_TOP);

            a[0] = center[0] - half; a[1] = center[1]; a[2] = center[2] + offset;
            b[0] = center[0] + half; b[1] = center[1]; b[2] = center[2] + offset;
            addLine(a, b, color, DepthMode.ALWAYS_ON_TOP);
        }
    }

    /** Appends an RGB axis triad transformed by a column-major 4x4 matrix, scaled. */
    public void addAxis(float[] transform4x4, float scale) {
        float[] origin = transformPoint(transform4x4, new float[]{0, 0, 0});
        float[] xEnd = transformPoint(transform4x4, new float[]{scale, 0, 0});
        float[] yEnd = transformPoint(transform4x4, new float[]{0, scale, 0});
        float[] zEnd = transformPoint(transform4x4, new float[]{0, 0, scale});

        addLine(origin, xEnd, new float[]{1, 0, 0, 1}, DepthMode.ALWAYS_ON_TOP);
        addLine(origin, yEnd, new float[]{0, 1, 0, 1}, DepthMode.ALWAYS_ON_TOP);
        addLine(origin, zEnd, new float[]{0, 0, 1, 1}, DepthMode.ALWAYS_ON_TOP);
    }

    private float[] transformPoint(float[] m, float[] p) {
        float x = p[0], y = p[1], z = p[2];
        float wx = m[0] * x + m[4] * y + m[8] * z + m[12];
        float wy = m[1] * x + m[5] * y + m[9] * z + m[13];
        float wz = m[2] * x + m[6] * y + m[10] * z + m[14];
        return new float[]{wx, wy, wz};
    }

    // --- Internal helpers ---

    private void appendTriVertex(float[] pos, float[] color, DepthMode mode) {
        if (mode == DepthMode.DEPTH_TESTED) {
            depthTestedTriData = ensureCapacity(depthTestedTriData, depthTestedTriCount + 1);
            depthTestedTriCount = appendVertex(depthTestedTriData, depthTestedTriCount, pos, color);
        } else {
            onTopTriData = ensureCapacity(onTopTriData, onTopTriCount + 1);
            onTopTriCount = appendVertex(onTopTriData, onTopTriCount, pos, color);
        }
    }

    private static int appendVertex(float[] data, int vertexCount, float[] pos, float[] color) {
        int off = vertexCount * FLOATS_PER_VERTEX;
        data[off]     = pos[0];
        data[off + 1] = pos[1];
        data[off + 2] = pos[2];
        data[off + 3] = color[0];
        data[off + 4] = color[1];
        data[off + 5] = color[2];
        data[off + 6] = color.length > 3 ? color[3] : 1.0f;
        return vertexCount + 1;
    }

    private static float[] ensureCapacity(float[] data, int vertexCount) {
        int required = vertexCount * FLOATS_PER_VERTEX;
        if (required <= data.length) return data;
        int newLen = data.length;
        while (newLen < required) newLen *= 2;
        float[] newData = new float[newLen];
        System.arraycopy(data, 0, newData, 0, data.length);
        return newData;
    }

    private float[] sub(float[] a, float[] b) {
        return new float[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private float[] cross(float[] a, float[] b) {
        return new float[]{
            a[1] * b[2] - a[2] * b[1],
            a[2] * b[0] - a[0] * b[2],
            a[0] * b[1] - a[1] * b[0]
        };
    }

    private float[] normalize(float[] v) {
        float len = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (len < 1e-8f) return new float[]{0, 0, 0};
        return new float[]{v[0] / len, v[1] / len, v[2] / len};
    }
}
