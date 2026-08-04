package io.github.yetyman.helpers.math.spatial.hex;

import io.github.yetyman.helpers.math.Vec2;

/**
 * Axial hex coordinate (q, r). The implicit third axis s = -q - r.
 * Uses cube coordinate constraint: q + r + s = 0.
 */
public class HexCoord {

    public int q;
    public int r;

    public HexCoord() {}
    public HexCoord(int q, int r) { this.q = q; this.r = r; }
    public HexCoord(HexCoord other) { this.q = other.q; this.r = other.r; }

    public int s() { return -q - r; }

    // --- Neighbors (6 directions) ---

    private static final int[][] DIRECTIONS = {
            {1, 0}, {1, -1}, {0, -1}, {-1, 0}, {-1, 1}, {0, 1}
    };

    public HexCoord neighbor(int direction) {
        int[] d = DIRECTIONS[direction % 6];
        return new HexCoord(q + d[0], r + d[1]);
    }

    public HexCoord[] neighbors() {
        HexCoord[] n = new HexCoord[6];
        for (int i = 0; i < 6; i++) n[i] = neighbor(i);
        return n;
    }

    // --- Distance ---

    public int distanceTo(HexCoord other) {
        return (Math.abs(q - other.q) + Math.abs(q + r - other.q - other.r) + Math.abs(r - other.r)) / 2;
    }

    public static int distance(HexCoord a, HexCoord b) {
        return a.distanceTo(b);
    }

    // --- Ring / Spiral ---

    /**
     * Returns all hex coordinates at exactly the given radius from this hex.
     */
    public HexCoord[] ring(int radius) {
        if (radius <= 0) return new HexCoord[]{new HexCoord(q, r)};
        HexCoord[] result = new HexCoord[6 * radius];
        HexCoord cur = new HexCoord(q + DIRECTIONS[4][0] * radius, r + DIRECTIONS[4][1] * radius);
        int idx = 0;
        for (int side = 0; side < 6; side++) {
            for (int step = 0; step < radius; step++) {
                result[idx++] = new HexCoord(cur.q, cur.r);
                cur = cur.neighbor(side);
            }
        }
        return result;
    }

    // --- Line drawing (linear interpolation, rounded) ---

    public HexCoord[] lineTo(HexCoord target) {
        int dist = distanceTo(target);
        if (dist == 0) return new HexCoord[]{new HexCoord(q, r)};
        HexCoord[] result = new HexCoord[dist + 1];
        for (int i = 0; i <= dist; i++) {
            float t = (float) i / dist;
            result[i] = hexRound(
                    q + (target.q - q) * t,
                    r + (target.r - r) * t
            );
        }
        return result;
    }

    private static HexCoord hexRound(float fq, float fr) {
        float fs = -fq - fr;
        int rq = Math.round(fq), rr = Math.round(fr), rs = Math.round(fs);
        float dq = Math.abs(rq - fq), dr = Math.abs(rr - fr), ds = Math.abs(rs - fs);
        if (dq > dr && dq > ds) rq = -rr - rs;
        else if (dr > ds) rr = -rq - rs;
        return new HexCoord(rq, rr);
    }

    // --- Range (all hexes within radius) ---

    public HexCoord[] range(int radius) {
        int count = 3 * radius * (radius + 1) + 1;
        HexCoord[] result = new HexCoord[count];
        int idx = 0;
        for (int dq = -radius; dq <= radius; dq++) {
            int r1 = Math.max(-radius, -dq - radius);
            int r2 = Math.min(radius, -dq + radius);
            for (int dr = r1; dr <= r2; dr++) {
                result[idx++] = new HexCoord(q + dq, r + dr);
            }
        }
        return result;
    }

    // --- Rotation (60 degree increments around origin) ---

    public HexCoord rotateCW() { return new HexCoord(-r, -s()); }
    public HexCoord rotateCCW() { return new HexCoord(-s(), -q); }

    // --- Reflection ---

    public HexCoord reflectQ() { return new HexCoord(q, s()); }
    public HexCoord reflectR() { return new HexCoord(s(), r); }
    public HexCoord reflectS() { return new HexCoord(r, q); }

    // --- Object overrides ---

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof HexCoord other)) return false;
        return q == other.q && r == other.r;
    }

    @Override
    public int hashCode() { return 31 * q + r; }

    @Override
    public String toString() { return "Hex(" + q + ", " + r + ")"; }
}
