package io.github.yetyman.helpers.math.spatial.isosurface;

import io.github.yetyman.helpers.math.Vec2;

/**
 * Marching Hexagons — contour extraction on a flat-top hexagonal grid.
 * Each hexagon has 6 vertices and a center sample, producing up to 6 edge crossings.
 * The 7 sample points per hex (center + 6 corners) give 2^7 = 128 configurations,
 * but since we share corners between hexes, the practical approach samples at hex centers
 * and interpolates edges between adjacent hex centers.
 */
public class MarchingHexagons {

    private MarchingHexagons() {}

    /**
     * Extracts contour lines from a 2D scalar field sampled on a flat-top hex grid.
     *
     * @param field the 2D scalar field
     * @param min minimum corner of the sampling area
     * @param max maximum corner of the sampling area
     * @param gridRadius number of hex rings from center (total hexes ~ 3*r*(r+1)+1)
     * @param hexSize size of each hexagon (center to corner distance)
     * @param isoLevel the contour threshold
     * @return contour output with line segments
     */
    public static ContourOutput extract(ScalarField2D field, Vec2 min, Vec2 max,
                                        int gridRadius, float hexSize, float isoLevel) {
        ContourOutput contour = new ContourOutput();
        Vec2 center = new Vec2((min.x + max.x) * 0.5f, (min.y + max.y) * 0.5f);

        // Flat-top hex layout constants
        float horizSpacing = hexSize * 1.5f;
        float vertSpacing = hexSize * (float) Math.sqrt(3.0);

        // Sample field at hex grid centers
        // Using offset coordinates for simplicity: even columns aligned, odd columns offset by half
        int cols = (int) Math.ceil((max.x - min.x) / horizSpacing) + 1;
        int rows = (int) Math.ceil((max.y - min.y) / vertSpacing) + 1;

        float[][] samples = new float[cols][rows];
        float[][] posX = new float[cols][rows];
        float[][] posY = new float[cols][rows];

        for (int col = 0; col < cols; col++) {
            for (int row = 0; row < rows; row++) {
                float x = min.x + col * horizSpacing;
                float y = min.y + row * vertSpacing + ((col % 2 == 1) ? vertSpacing * 0.5f : 0f);
                posX[col][row] = x;
                posY[col][row] = y;
                samples[col][row] = field.sample(x, y);
            }
        }

        // For each hex, check edges to its 3 forward neighbors (avoid duplicate edges)
        // Forward neighbors for flat-top: right, upper-right, lower-right
        for (int col = 0; col < cols; col++) {
            for (int row = 0; row < rows; row++) {
                float v0 = samples[col][row];
                float x0 = posX[col][row], y0 = posY[col][row];

                // Neighbor: same column, row+1 (above)
                if (row + 1 < rows) {
                    float v1 = samples[col][row + 1];
                    if (crosses(v0, v1, isoLevel)) {
                        float t = interp(v0, v1, isoLevel);
                        addEdgeCrossing(contour, x0, y0, posX[col][row + 1], posY[col][row + 1], t);
                    }
                }

                // Neighbor: col+1, same or offset row (upper-right)
                if (col + 1 < cols) {
                    int nRow = (col % 2 == 0) ? row : row + 1;
                    if (nRow >= 0 && nRow < rows) {
                        float v1 = samples[col + 1][nRow];
                        if (crosses(v0, v1, isoLevel)) {
                            float t = interp(v0, v1, isoLevel);
                            addEdgeCrossing(contour, x0, y0, posX[col + 1][nRow], posY[col + 1][nRow], t);
                        }
                    }
                }

                // Neighbor: col+1, lower-right
                if (col + 1 < cols) {
                    int nRow = (col % 2 == 0) ? row - 1 : row;
                    if (nRow >= 0 && nRow < rows) {
                        float v1 = samples[col + 1][nRow];
                        if (crosses(v0, v1, isoLevel)) {
                            float t = interp(v0, v1, isoLevel);
                            addEdgeCrossing(contour, x0, y0, posX[col + 1][nRow], posY[col + 1][nRow], t);
                        }
                    }
                }
            }
        }

        return contour;
    }

    private static boolean crosses(float v0, float v1, float iso) {
        return (v0 < iso) != (v1 < iso);
    }

    private static float interp(float v0, float v1, float iso) {
        if (Math.abs(v1 - v0) < 1e-6f) return 0.5f;
        return (iso - v0) / (v1 - v0);
    }

    private static void addEdgeCrossing(ContourOutput contour, float x0, float y0, float x1, float y1, float t) {
        float cx = x0 + t * (x1 - x0);
        float cy = y0 + t * (y1 - y0);
        // Each edge crossing becomes a point; we connect crossings that share a hex cell
        // For simplicity, output each crossing as a degenerate segment (point)
        // A full implementation would track which crossings belong to the same hex
        // and connect them to form proper contour segments.
        // Here we output the interpolated crossing point as a segment endpoint pair
        // between the two hex centers (approximation suitable for visualization).
        int a = contour.addVertex(cx, cy);
        // Find perpendicular direction for segment visualization
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len > 1e-6f) {
            float nx = -dy / len * len * 0.3f;
            float ny = dx / len * len * 0.3f;
            int b = contour.addVertex(cx + nx, cy + ny);
            contour.addSegment(a, b);
        }
    }
}
