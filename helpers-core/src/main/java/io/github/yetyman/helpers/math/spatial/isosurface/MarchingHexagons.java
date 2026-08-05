package io.github.yetyman.helpers.math.spatial.isosurface;

import io.github.yetyman.helpers.math.Vec2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Marching Hexagons — contour extraction on a flat-top hexagonal grid using axial coordinates.
 * Avoids offset-coordinate neighbor asymmetry bugs by using axial (q,r) coordinates
 * with the standard 6 direction vectors.
 */
public class MarchingHexagons {

    private MarchingHexagons() {}

    // Axial hex directions (always symmetric: direction i and direction (i+3)%6 are opposites)
    private static final int[][] AXIAL_DIRS = {{1,0},{0,1},{-1,1},{-1,0},{0,-1},{1,-1}};

    public static ContourOutput extract(ScalarField2D field, Vec2 min, Vec2 max,
                                        int gridRadius, float hexSize, float isoLevel) {
        ContourOutput contour = new ContourOutput();

        Vec2 center = new Vec2((min.x + max.x) * 0.5f, (min.y + max.y) * 0.5f);

        // Flat-top axial to pixel conversion
        // x = hexSize * (3/2 * q)
        // y = hexSize * (sqrt(3)/2 * q + sqrt(3) * r)
        float sqrt3 = (float) Math.sqrt(3.0);

        // Determine radius needed to cover sampling area
        float maxDist = Math.max(max.x - min.x, max.y - min.y) * 0.5f;
        int radius = (int) Math.ceil(maxDist / (hexSize * 1.5f)) + 2;

        // Sample field at all hex centers using axial coordinates
        Map<Long, float[]> cells = new HashMap<>(); // key(q,r) -> {x, y, value}

        for (int q = -radius; q <= radius; q++) {
            for (int r = -radius; r <= radius; r++) {
                if (Math.abs(q + r) > radius) continue; // stay within hex-shaped region
                float x = center.x + hexSize * (1.5f * q);
                float y = center.y + hexSize * (sqrt3 * 0.5f * q + sqrt3 * r);
                float value = field.sample(x, y);
                cells.put(key(q, r), new float[]{x, y, value});
            }
        }

        // For each cell, find crossings and pair them by direction order
        for (var entry : cells.entrySet()) {
            long k = entry.getKey();
            float[] cell = entry.getValue();
            int q = (int)(k >> 32), r = (int)(k & 0xFFFFFFFFL);

            List<int[]> crossingDirs = new ArrayList<>();

            for (int dir = 0; dir < 6; dir++) {
                int nq = q + AXIAL_DIRS[dir][0], nr = r + AXIAL_DIRS[dir][1];
                float[] neighbor = cells.get(key(nq, nr));
                if (neighbor == null) continue;

                float v0 = cell[2], v1 = neighbor[2];
                if ((v0 < isoLevel) != (v1 < isoLevel)) {
                    float t = (isoLevel - v0) / (v1 - v0);
                    float cx = cell[0] + t * (neighbor[0] - cell[0]);
                    float cy = cell[1] + t * (neighbor[1] - cell[1]);
                    int vi = contour.addVertex(cx, cy);
                    crossingDirs.add(new int[]{dir, vi});
                }
            }

            // Connect crossings within this cell
            if (crossingDirs.size() >= 2) {
                crossingDirs.sort((a, b) -> Integer.compare(a[0], b[0]));
                for (int i = 0; i < crossingDirs.size() - 1; i++) {
                    contour.addSegment(crossingDirs.get(i)[1], crossingDirs.get(i + 1)[1]);
                }
                if (crossingDirs.size() > 2) {
                    contour.addSegment(crossingDirs.get(crossingDirs.size() - 1)[1], crossingDirs.get(0)[1]);
                }
            }
        }

        return contour;
    }

    private static long key(int q, int r) {
        return ((long) q << 32) | (r & 0xFFFFFFFFL);
    }
}
