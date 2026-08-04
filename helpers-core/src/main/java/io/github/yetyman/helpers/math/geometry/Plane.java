package io.github.yetyman.helpers.math.geometry;

import io.github.yetyman.helpers.math.BuildStrategy;
import io.github.yetyman.helpers.math.MathUtil;
import io.github.yetyman.helpers.math.Vec3;

/**
 * Plane defined by normal (nx, ny, nz) and signed distance d.
 * The plane equation is: nx*x + ny*y + nz*z + d = 0.
 */
public class Plane {

    private static BuildStrategy<Plane> buildStrategy = BuildStrategy.allocating(Plane::new);

    public float nx, ny, nz, d;

    public Plane() {}

    public Plane(float nx, float ny, float nz, float d) {
        this.nx = nx;
        this.ny = ny;
        this.nz = nz;
        this.d = d;
    }

    public static Plane fromPointNormal(Vec3 point, Vec3 normal) {
        Plane p = new Plane();
        p.nx = normal.x;
        p.ny = normal.y;
        p.nz = normal.z;
        p.d = -(normal.x * point.x + normal.y * point.y + normal.z * point.z);
        return p;
    }

    public static Plane fromThreePoints(Vec3 a, Vec3 b, Vec3 c) {
        float abx = b.x - a.x, aby = b.y - a.y, abz = b.z - a.z;
        float acx = c.x - a.x, acy = c.y - a.y, acz = c.z - a.z;
        float nx = aby * acz - abz * acy;
        float ny = abz * acx - abx * acz;
        float nz = abx * acy - aby * acx;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > MathUtil.EPSILON) {
            float inv = 1f / len;
            nx *= inv; ny *= inv; nz *= inv;
        }
        Plane p = new Plane();
        p.nx = nx; p.ny = ny; p.nz = nz;
        p.d = -(nx * a.x + ny * a.y + nz * a.z);
        return p;
    }

    public float distanceTo(Vec3 point) {
        return nx * point.x + ny * point.y + nz * point.z + d;
    }

    public enum Side { FRONT, BACK, ON }

    public Side classify(Vec3 point) {
        float dist = distanceTo(point);
        if (dist > MathUtil.EPSILON) return Side.FRONT;
        if (dist < -MathUtil.EPSILON) return Side.BACK;
        return Side.ON;
    }

    public Plane normalize() {
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > MathUtil.EPSILON) {
            float inv = 1f / len;
            nx *= inv; ny *= inv; nz *= inv; d *= inv;
        }
        return this;
    }

    @Override
    public String toString() {
        return "Plane(" + nx + ", " + ny + ", " + nz + ", " + d + ")";
    }

    // --- BuildStrategy ---

    public static void setBuildStrategy(BuildStrategy<Plane> strategy) { buildStrategy = strategy; }
    public static BuildStrategy<Plane> getBuildStrategy() { return buildStrategy; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private float nx, ny, nz, d;
        private Vec3 point, normal;
        private Vec3 a, b, c;
        private final boolean eager;

        private enum Mode { DIRECT, POINT_NORMAL, THREE_POINTS }
        private Mode mode = Mode.DIRECT;

        public Builder() { this.eager = false; }
        private Builder(boolean eager) { this.eager = eager; }

        /** Returns a builder that will eagerly cache intermediate results (no-op currently). */
        public static Builder eager() { return new Builder(true); }
        /** Returns a builder that defers all computation to build() time (default). */
        public static Builder lazy() { return new Builder(false); }

        public Builder normal(float nx, float ny, float nz) { this.nx = nx; this.ny = ny; this.nz = nz; this.mode = Mode.DIRECT; return this; }
        public Builder normal(Vec3 n) { this.nx = n.x; this.ny = n.y; this.nz = n.z; this.mode = Mode.DIRECT; return this; }
        public Builder d(float d) { this.d = d; return this; }

        public Builder fromPointNormal(Vec3 point, Vec3 normal) {
            this.point = point; this.normal = normal;
            this.mode = Mode.POINT_NORMAL;
            return this;
        }

        public Builder fromThreePoints(Vec3 a, Vec3 b, Vec3 c) {
            this.a = a; this.b = b; this.c = c;
            this.mode = Mode.THREE_POINTS;
            return this;
        }

        public Plane build() {
            return apply(buildStrategy.obtain());
        }

        public Plane build(BuildStrategy<Plane> strategy) {
            return apply(strategy.obtain());
        }

        // NOTE: apply() could be invoked eagerly to cache intermediate results when parameters change,
        // but is not yet implemented that way. Currently all computation happens at build() time.
        private Plane apply(Plane p) {
            switch (mode) {
                case DIRECT -> { p.nx = nx; p.ny = ny; p.nz = nz; p.d = d; }
                case POINT_NORMAL -> {
                    p.nx = normal.x; p.ny = normal.y; p.nz = normal.z;
                    p.d = -(normal.x * point.x + normal.y * point.y + normal.z * point.z);
                }
                case THREE_POINTS -> {
                    Plane tmp = Plane.fromThreePoints(a, b, c);
                    p.nx = tmp.nx; p.ny = tmp.ny; p.nz = tmp.nz; p.d = tmp.d;
                }
            }
            return p;
        }
    }
}
