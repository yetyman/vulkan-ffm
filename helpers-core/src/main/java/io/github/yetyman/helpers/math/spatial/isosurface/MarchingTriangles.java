package io.github.yetyman.helpers.math.spatial.isosurface;

import io.github.yetyman.helpers.math.Vec2;

/**
 * Marching Triangles — contour extraction on an equilateral triangulated grid.
 * Each triangle has 3 edges, 2^3 = 8 configurations, zero ambiguity.
 * Uses offset rows (half-step shift on odd rows) to form equilateral triangles.
 */
public class MarchingTriangles {

    private MarchingTriangles() {}

    public static ContourOutput extract(ScalarField2D field, Vec2 min, Vec2 max,
                                        int resX, int resY, float isoLevel) {
        ContourOutput contour = new ContourOutput();
        float stepX = (max.x - min.x) / resX;
        float triHeight = stepX * (float)(Math.sqrt(3.0) / 2.0);
        int rows = (int)((max.y - min.y) / triHeight);
        int cols = resX;

        // Build vertex grid: even rows start at min.x, odd rows offset by stepX/2
        float[][] vx = new float[rows + 1][cols + 1];
        float[][] vy = new float[rows + 1][cols + 1];
        float[][] val = new float[rows + 1][cols + 1];

        for (int r = 0; r <= rows; r++) {
            float y = min.y + r * triHeight;
            float offset = (r % 2 == 1) ? stepX * 0.5f : 0f;
            for (int c = 0; c <= cols; c++) {
                float x = min.x + c * stepX + offset;
                vx[r][c] = x;
                vy[r][c] = y;
                val[r][c] = field.sample(x, y);
            }
        }

        // Form triangles between adjacent rows
        // Even row (r) to odd row (r+1): odd row is shifted right by half step
        //   Triangle A: (r,c), (r,c+1), (r+1,c)     — points up-left
        //   Triangle B: (r,c+1), (r+1,c+1), (r+1,c) — points down-right
        // Odd row (r) to even row (r+1): even row is shifted left by half step
        //   Triangle A: (r,c), (r,c+1), (r+1,c+1)   — points up-right
        //   Triangle B: (r,c), (r+1,c+1), (r+1,c)   — points down-left

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (r % 2 == 0) {
                    // Even->Odd: next row offset right
                    // Up triangle: (r,c), (r,c+1), (r+1,c)
                    march(contour, vx[r][c], vy[r][c], val[r][c],
                                   vx[r][c+1], vy[r][c+1], val[r][c+1],
                                   vx[r+1][c], vy[r+1][c], val[r+1][c], isoLevel);
                    // Down triangle: (r,c+1), (r+1,c+1), (r+1,c)
                    march(contour, vx[r][c+1], vy[r][c+1], val[r][c+1],
                                   vx[r+1][c+1], vy[r+1][c+1], val[r+1][c+1],
                                   vx[r+1][c], vy[r+1][c], val[r+1][c], isoLevel);
                } else {
                    // Odd->Even: next row offset left
                    // Up triangle: (r,c), (r,c+1), (r+1,c+1)
                    march(contour, vx[r][c], vy[r][c], val[r][c],
                                   vx[r][c+1], vy[r][c+1], val[r][c+1],
                                   vx[r+1][c+1], vy[r+1][c+1], val[r+1][c+1], isoLevel);
                    // Down triangle: (r,c), (r+1,c+1), (r+1,c)
                    march(contour, vx[r][c], vy[r][c], val[r][c],
                                   vx[r+1][c+1], vy[r+1][c+1], val[r+1][c+1],
                                   vx[r+1][c], vy[r+1][c], val[r+1][c], isoLevel);
                }
            }
        }

        return contour;
    }

    private static void march(ContourOutput contour,
                              float x0, float y0, float v0,
                              float x1, float y1, float v1,
                              float x2, float y2, float v2, float iso) {
        boolean in0 = v0 < iso, in1 = v1 < iso, in2 = v2 < iso;

        // All same side — no crossing
        if (in0 == in1 && in1 == in2) return;

        // Find the 2 edges that cross the iso-level and interpolate crossing positions
        float[] crossings = new float[4]; // up to 2 crossings, each (x, y)
        int count = 0;

        if (in0 != in1) { // edge 0-1 crosses
            float t = (iso - v0) / (v1 - v0);
            crossings[count * 2] = x0 + t * (x1 - x0);
            crossings[count * 2 + 1] = y0 + t * (y1 - y0);
            count++;
        }
        if (in1 != in2) { // edge 1-2 crosses
            float t = (iso - v1) / (v2 - v1);
            crossings[count * 2] = x1 + t * (x2 - x1);
            crossings[count * 2 + 1] = y1 + t * (y2 - y1);
            count++;
        }
        if (in2 != in0) { // edge 2-0 crosses
            float t = (iso - v2) / (v0 - v2);
            crossings[count * 2] = x2 + t * (x0 - x2);
            crossings[count * 2 + 1] = y2 + t * (y0 - y2);
            count++;
        }

        if (count == 2) {
            int ia = contour.addVertex(crossings[0], crossings[1]);
            int ib = contour.addVertex(crossings[2], crossings[3]);
            contour.addSegment(ia, ib);
        }
    }

    // Unused — kept for reference
    @SuppressWarnings("unused")
    private static float t(float va, float vb, float iso) {
        float d = vb - va;
        if (Math.abs(d) < 1e-6f) return 0.5f;
        return (iso - va) / d;
    }
}
