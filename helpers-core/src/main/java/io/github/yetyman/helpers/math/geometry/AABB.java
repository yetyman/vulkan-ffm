package io.github.yetyman.helpers.math.geometry;

import io.github.yetyman.helpers.math.BuildStrategy;
import io.github.yetyman.helpers.math.Mat4;
import io.github.yetyman.helpers.math.Vec3;

/**
 * Axis-aligned bounding box defined by min and max corners.
 */
public class AABB {

    private static BuildStrategy<AABB> buildStrategy = BuildStrategy.allocating(AABB::new);

    public final Vec3 min;
    public final Vec3 max;

    public AABB() {
        this.min = new Vec3();
        this.max = new Vec3();
    }

    public AABB(Vec3 min, Vec3 max) {
        this.min = min;
        this.max = max;
    }

    public static AABB fromMinMax(Vec3 min, Vec3 max) {
        return new AABB(new Vec3(min), new Vec3(max));
    }

    public static AABB fromCenterExtents(Vec3 center, Vec3 extents) {
        return new AABB(center.subNew(extents), center.addNew(extents));
    }

    public boolean contains(Vec3 point) {
        return point.x >= min.x && point.x <= max.x
            && point.y >= min.y && point.y <= max.y
            && point.z >= min.z && point.z <= max.z;
    }

    public boolean contains(AABB other) {
        return other.min.x >= min.x && other.max.x <= max.x
            && other.min.y >= min.y && other.max.y <= max.y
            && other.min.z >= min.z && other.max.z <= max.z;
    }

    public AABB expand(Vec3 point) {
        min.min(point);
        max.max(point);
        return this;
    }

    public AABB merge(AABB other) {
        min.min(other.min);
        max.max(other.max);
        return this;
    }

    public Vec3 center() {
        return new Vec3(
                (min.x + max.x) * 0.5f,
                (min.y + max.y) * 0.5f,
                (min.z + max.z) * 0.5f
        );
    }

    public Vec3 extents() {
        return new Vec3(
                (max.x - min.x) * 0.5f,
                (max.y - min.y) * 0.5f,
                (max.z - min.z) * 0.5f
        );
    }

    public Vec3 size() {
        return new Vec3(max.x - min.x, max.y - min.y, max.z - min.z);
    }

    public boolean intersects(AABB other) {
        return min.x <= other.max.x && max.x >= other.min.x
            && min.y <= other.max.y && max.y >= other.min.y
            && min.z <= other.max.z && max.z >= other.min.z;
    }

    /**
     * Returns a new AABB enclosing this box after transformation by the given matrix.
     */
    public AABB transform(Mat4 m) {
        Vec3 newMin = new Vec3(m.m30, m.m31, m.m32);
        Vec3 newMax = new Vec3(m.m30, m.m31, m.m32);
        float[] mn = {min.x, min.y, min.z};
        float[] mx = {max.x, max.y, max.z};
        float[][] cols = {
                {m.m00, m.m01, m.m02},
                {m.m10, m.m11, m.m12},
                {m.m20, m.m21, m.m22}
        };
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                float a = cols[i][j] * mn[i];
                float b = cols[i][j] * mx[i];
                if (a < b) {
                    addComponent(newMin, j, a);
                    addComponent(newMax, j, b);
                } else {
                    addComponent(newMin, j, b);
                    addComponent(newMax, j, a);
                }
            }
        }
        return new AABB(newMin, newMax);
    }

    private static void addComponent(Vec3 v, int index, float value) {
        switch (index) {
            case 0 -> v.x += value;
            case 1 -> v.y += value;
            case 2 -> v.z += value;
        }
    }

    @Override
    public String toString() {
        return "AABB(min=" + min + ", max=" + max + ")";
    }

    // --- BuildStrategy ---

    public static void setBuildStrategy(BuildStrategy<AABB> strategy) { buildStrategy = strategy; }
    public static BuildStrategy<AABB> getBuildStrategy() { return buildStrategy; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final Vec3 min = new Vec3();
        private final Vec3 max = new Vec3();
        private Vec3 center, extents;
        private final boolean eager;

        private enum Mode { MIN_MAX, CENTER_EXTENTS }
        private Mode mode = Mode.MIN_MAX;

        public Builder() { this.eager = false; }
        private Builder(boolean eager) { this.eager = eager; }

        /** Returns a builder that will eagerly cache intermediate results (no-op currently). */
        public static Builder eager() { return new Builder(true); }
        /** Returns a builder that defers all computation to build() time (default). */
        public static Builder lazy() { return new Builder(false); }

        public Builder min(Vec3 min) { this.min.set(min); this.mode = Mode.MIN_MAX; return this; }
        public Builder min(float x, float y, float z) { this.min.set(x, y, z); this.mode = Mode.MIN_MAX; return this; }
        public Builder max(Vec3 max) { this.max.set(max); this.mode = Mode.MIN_MAX; return this; }
        public Builder max(float x, float y, float z) { this.max.set(x, y, z); this.mode = Mode.MIN_MAX; return this; }

        public Builder fromCenterExtents(Vec3 center, Vec3 extents) {
            this.center = center; this.extents = extents;
            this.mode = Mode.CENTER_EXTENTS;
            return this;
        }

        public AABB build() {
            return apply(buildStrategy.obtain());
        }

        public AABB build(BuildStrategy<AABB> strategy) {
            return apply(strategy.obtain());
        }

        // NOTE: apply() could be invoked eagerly to cache intermediate results when parameters change,
        // but is not yet implemented that way. Currently all computation happens at build() time.
        private AABB apply(AABB a) {
            switch (mode) {
                case MIN_MAX -> { a.min.set(min); a.max.set(max); }
                case CENTER_EXTENTS -> { a.min.set(center).sub(extents); a.max.set(center).add(extents); }
            }
            return a;
        }
    }
}
