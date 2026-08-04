package io.github.yetyman.helpers.math.geometry;

import io.github.yetyman.helpers.math.BuildStrategy;
import io.github.yetyman.helpers.math.Vec3;

/**
 * Bounding sphere defined by center and radius.
 */
public class Sphere {

    private static BuildStrategy<Sphere> buildStrategy = BuildStrategy.allocating(Sphere::new);

    public final Vec3 center;
    public float radius;

    public Sphere() {
        this.center = new Vec3();
        this.radius = 0f;
    }

    public Sphere(Vec3 center, float radius) {
        this.center = center;
        this.radius = radius;
    }

    public boolean contains(Vec3 point) {
        return center.distanceSquared(point) <= radius * radius;
    }

    public boolean intersects(Sphere other) {
        float r = radius + other.radius;
        return center.distanceSquared(other.center) <= r * r;
    }

    public Sphere merge(Sphere other) {
        float dist = center.distance(other.center);
        if (dist + other.radius <= radius) return new Sphere(new Vec3(center), radius);
        if (dist + radius <= other.radius) return new Sphere(new Vec3(other.center), other.radius);
        float newRadius = (dist + radius + other.radius) * 0.5f;
        float t = (newRadius - radius) / dist;
        Vec3 newCenter = center.lerpNew(other.center, t);
        return new Sphere(newCenter, newRadius);
    }

    @Override
    public String toString() {
        return "Sphere(center=" + center + ", r=" + radius + ")";
    }

    // --- BuildStrategy ---

    public static void setBuildStrategy(BuildStrategy<Sphere> strategy) { buildStrategy = strategy; }
    public static BuildStrategy<Sphere> getBuildStrategy() { return buildStrategy; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final Vec3 center = new Vec3();
        private float radius;
        private final boolean eager;

        public Builder() { this.eager = false; }
        private Builder(boolean eager) { this.eager = eager; }

        /** Returns a builder that will eagerly cache intermediate results (no-op currently). */
        public static Builder eager() { return new Builder(true); }
        /** Returns a builder that defers all computation to build() time (default). */
        public static Builder lazy() { return new Builder(false); }

        public Builder center(Vec3 c) { this.center.set(c); return this; }
        public Builder center(float x, float y, float z) { this.center.set(x, y, z); return this; }
        public Builder radius(float r) { this.radius = r; return this; }

        public Sphere build() {
            Sphere s = buildStrategy.obtain();
            s.center.set(center);
            s.radius = radius;
            return s;
        }

        public Sphere build(BuildStrategy<Sphere> strategy) {
            Sphere s = strategy.obtain();
            s.center.set(center);
            s.radius = radius;
            return s;
        }
    }
}
