package io.github.yetyman.vulkan.foundation.ui.layers.scene3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Accumulates 3D overlay draw primitives (lines, wire boxes, spheres, grids, axes) for a single
 * frame into flat vertex lists, split by topology (line vs triangle) and DepthMode. Cleared at
 * the start of each frame by Scene3DOverlayLayer.update().
 *
 * All higher-level shapes (wire box, wire sphere, grid, axis, arrow) are tessellated into raw
 * line/triangle vertices here rather than being their own GPU primitive - this keeps
 * OverlayRenderer's draw path to exactly four vertex buffers/draw calls per frame regardless of
 * how many shapes were requested.
 */
public class OverlayDrawList {
    private final List<OverlayVertex> depthTestedLines = new ArrayList<>();
    private final List<OverlayVertex> onTopLines = new ArrayList<>();
    private final List<OverlayVertex> depthTestedTris = new ArrayList<>();
    private final List<OverlayVertex> onTopTris = new ArrayList<>();

    /** Clears all accumulated primitives. Called once per frame before draw calls. */
    public void clear() {
        depthTestedLines.clear();
        onTopLines.clear();
        depthTestedTris.clear();
        onTopTris.clear();
    }

    /** @return true if nothing has been accumulated this frame. */
    public boolean isEmpty() {
        return depthTestedLines.isEmpty() && onTopLines.isEmpty()
            && depthTestedTris.isEmpty() && onTopTris.isEmpty();
    }

    public List<OverlayVertex> depthTestedLines() { return depthTestedLines; }
    public List<OverlayVertex> onTopLines() { return onTopLines; }
    public List<OverlayVertex> depthTestedTris() { return depthTestedTris; }
    public List<OverlayVertex> onTopTris() { return onTopTris; }

    private List<OverlayVertex> lineList(DepthMode mode) {
        return mode == DepthMode.DEPTH_TESTED ? depthTestedLines : onTopLines;
    }

    private List<OverlayVertex> triList(DepthMode mode) {
        return mode == DepthMode.DEPTH_TESTED ? depthTestedTris : onTopTris;
    }

    /** Appends a single line segment from `from` to `to`. */
    public void addLine(float[] from, float[] to, float[] color, DepthMode mode) {
        List<OverlayVertex> list = lineList(mode);
        list.add(vertex(from, color));
        list.add(vertex(to, color));
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
            {0, 1}, {1, 2}, {2, 3}, {3, 0}, // bottom face
            {4, 5}, {5, 6}, {6, 7}, {7, 4}, // top face
            {0, 4}, {1, 5}, {2, 6}, {3, 7}  // verticals
        };
        List<OverlayVertex> list = lineList(mode);
        for (int[] edge : edges) {
            list.add(vertex(corners[edge[0]], color));
            list.add(vertex(corners[edge[1]], color));
        }
    }

    /** Appends a wire sphere built from three orthogonal circles (XY, XZ, YZ planes). */
    public void addWireSphere(float[] center, float radius, float[] color, int segments, DepthMode mode) {
        List<OverlayVertex> list = lineList(mode);
        addCircle(list, center, radius, segments, color, 0); // XY plane
        addCircle(list, center, radius, segments, color, 1); // XZ plane
        addCircle(list, center, radius, segments, color, 2); // YZ plane
    }

    private void addCircle(List<OverlayVertex> list, float[] center, float radius, int segments,
                            float[] color, int plane) {
        for (int i = 0; i < segments; i++) {
            float a0 = (float) (2.0 * Math.PI * i / segments);
            float a1 = (float) (2.0 * Math.PI * (i + 1) / segments);
            float[] p0 = circlePoint(center, radius, a0, plane);
            float[] p1 = circlePoint(center, radius, a1, plane);
            list.add(vertex(p0, color));
            list.add(vertex(p1, color));
        }
    }

    private float[] circlePoint(float[] center, float radius, float angle, int plane) {
        float c = (float) Math.cos(angle) * radius;
        float s = (float) Math.sin(angle) * radius;
        return switch (plane) {
            case 0 -> new float[]{center[0] + c, center[1] + s, center[2]};        // XY
            case 1 -> new float[]{center[0] + c, center[1], center[2] + s};        // XZ
            default -> new float[]{center[0], center[1] + c, center[2] + s};       // YZ
        };
    }

    /** Appends a ray as a single line of the given length from origin along direction (not normalized by this call). */
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

        List<OverlayVertex> tris = triList(DepthMode.ALWAYS_ON_TOP);
        tris.add(vertex(to, color));
        tris.add(vertex(backLeft, color));
        tris.add(vertex(backRight, color));
    }

    /** Appends a wireframe frustum from an inverse view-projection matrix (column-major 4x4, 16 floats). */
    public void addFrustum(float[] inverseViewProj, float[] color) {
        float[][] ndcCorners = {
            {-1, -1, 0}, {1, -1, 0}, {1, 1, 0}, {-1, 1, 0}, // near plane
            {-1, -1, 1}, {1, -1, 1}, {1, 1, 1}, {-1, 1, 1}  // far plane
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
        List<OverlayVertex> list = lineList(DepthMode.ALWAYS_ON_TOP);
        for (int[] edge : edges) {
            list.add(vertex(worldCorners[edge[0]], color));
            list.add(vertex(worldCorners[edge[1]], color));
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
        List<OverlayVertex> list = lineList(DepthMode.ALWAYS_ON_TOP);
        float half = size * 0.5f;
        float step = size / divisions;
        for (int i = 0; i <= divisions; i++) {
            float offset = -half + i * step;
            float[] a1 = {center[0] + offset, center[1], center[2] - half};
            float[] b1 = {center[0] + offset, center[1], center[2] + half};
            list.add(vertex(a1, color));
            list.add(vertex(b1, color));

            float[] a2 = {center[0] - half, center[1], center[2] + offset};
            float[] b2 = {center[0] + half, center[1], center[2] + offset};
            list.add(vertex(a2, color));
            list.add(vertex(b2, color));
        }
    }

    /** Appends an RGB axis triad (X=red, Y=green, Z=blue) transformed by a column-major 4x4 matrix, scaled. */
    public void addAxis(float[] transform4x4, float scale) {
        float[] origin = transformPoint(transform4x4, new float[]{0, 0, 0});
        float[] xEnd = transformPoint(transform4x4, new float[]{scale, 0, 0});
        float[] yEnd = transformPoint(transform4x4, new float[]{0, scale, 0});
        float[] zEnd = transformPoint(transform4x4, new float[]{0, 0, scale});

        List<OverlayVertex> list = lineList(DepthMode.ALWAYS_ON_TOP);
        list.add(vertex(origin, new float[]{1, 0, 0, 1}));
        list.add(vertex(xEnd, new float[]{1, 0, 0, 1}));
        list.add(vertex(origin, new float[]{0, 1, 0, 1}));
        list.add(vertex(yEnd, new float[]{0, 1, 0, 1}));
        list.add(vertex(origin, new float[]{0, 0, 1, 1}));
        list.add(vertex(zEnd, new float[]{0, 0, 1, 1}));
    }

    private float[] transformPoint(float[] m, float[] p) {
        float x = p[0], y = p[1], z = p[2];
        float wx = m[0] * x + m[4] * y + m[8] * z + m[12];
        float wy = m[1] * x + m[5] * y + m[9] * z + m[13];
        float wz = m[2] * x + m[6] * y + m[10] * z + m[14];
        return new float[]{wx, wy, wz};
    }

    private OverlayVertex vertex(float[] pos, float[] color) {
        float a = color.length > 3 ? color[3] : 1.0f;
        return new OverlayVertex(pos[0], pos[1], pos[2], color[0], color[1], color[2], a);
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
