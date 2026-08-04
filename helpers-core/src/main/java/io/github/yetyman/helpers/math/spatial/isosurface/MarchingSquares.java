package io.github.yetyman.helpers.math.spatial.isosurface;

import io.github.yetyman.helpers.math.Vec2;

/**
 * Marching Squares contour extraction.
 * Takes a 2D scalar field and grid parameters, produces line segments forming the contour.
 */
public class MarchingSquares {

    private MarchingSquares() {}

    /**
     * Extracts contour lines at the given threshold from a 2D scalar field.
     */
    public static ContourOutput extract(ScalarField2D field, Vec2 min, Vec2 max,
                                        int resX, int resY, float isoLevel) {
        ContourOutput contour = new ContourOutput();
        float stepX = (max.x - min.x) / resX;
        float stepY = (max.y - min.y) / resY;

        float[][] grid = new float[resX + 1][resY + 1];
        for (int x = 0; x <= resX; x++)
            for (int y = 0; y <= resY; y++)
                grid[x][y] = field.sample(min.x + x * stepX, min.y + y * stepY);

        for (int x = 0; x < resX; x++)
            for (int y = 0; y < resY; y++)
                processCell(contour, grid, x, y, min, stepX, stepY, isoLevel);

        return contour;
    }

    private static void processCell(ContourOutput contour, float[][] grid,
                                    int x, int y, Vec2 min, float stepX, float stepY, float isoLevel) {
        float bl = grid[x][y];       // bottom-left
        float br = grid[x+1][y];     // bottom-right
        float tr = grid[x+1][y+1];   // top-right
        float tl = grid[x][y+1];     // top-left

        int cellIndex = 0;
        if (bl < isoLevel) cellIndex |= 1;
        if (br < isoLevel) cellIndex |= 2;
        if (tr < isoLevel) cellIndex |= 4;
        if (tl < isoLevel) cellIndex |= 8;

        if (cellIndex == 0 || cellIndex == 15) return;

        float px = min.x + x * stepX;
        float py = min.y + y * stepY;

        // Edge midpoints (with interpolation)
        float bottomX = px + interp(bl, br, isoLevel) * stepX;
        float bottomY = py;
        float rightX = px + stepX;
        float rightY = py + interp(br, tr, isoLevel) * stepY;
        float topX = px + interp(tl, tr, isoLevel) * stepX;
        float topY = py + stepY;
        float leftX = px;
        float leftY = py + interp(bl, tl, isoLevel) * stepY;

        switch (cellIndex) {
            case 1, 14 -> addSegment(contour, leftX, leftY, bottomX, bottomY);
            case 2, 13 -> addSegment(contour, bottomX, bottomY, rightX, rightY);
            case 3, 12 -> addSegment(contour, leftX, leftY, rightX, rightY);
            case 4, 11 -> addSegment(contour, rightX, rightY, topX, topY);
            case 5 -> { addSegment(contour, leftX, leftY, topX, topY); addSegment(contour, bottomX, bottomY, rightX, rightY); }
            case 6, 9 -> addSegment(contour, bottomX, bottomY, topX, topY);
            case 7, 8 -> addSegment(contour, leftX, leftY, topX, topY);
            case 10 -> { addSegment(contour, leftX, leftY, bottomX, bottomY); addSegment(contour, rightX, rightY, topX, topY); }
        }
    }

    private static void addSegment(ContourOutput contour, float x1, float y1, float x2, float y2) {
        int a = contour.addVertex(x1, y1);
        int b = contour.addVertex(x2, y2);
        contour.addSegment(a, b);
    }

    private static float interp(float v1, float v2, float isoLevel) {
        if (Math.abs(v2 - v1) < 1e-6f) return 0.5f;
        return (isoLevel - v1) / (v2 - v1);
    }
}
