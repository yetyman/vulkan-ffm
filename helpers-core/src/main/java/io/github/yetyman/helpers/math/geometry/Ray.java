package io.github.yetyman.helpers.math.geometry;

import io.github.yetyman.helpers.math.BuildStrategy;
import io.github.yetyman.helpers.math.Vec3;

/**
 * Ray defined by origin and direction (direction should be unit length).
 */
public class Ray {

    private static BuildStrategy<Ray> buildStrategy = BuildStrategy.allocating(Ray::new);

    public final Vec3 origin;
    public final Vec3 direction;

    public Ray() {
        this.origin = new Vec3();
        this.direction = new Vec3(0f, 0f, -1f);
    }

    public Ray(Vec3 origin, Vec3 direction) {
        this.origin = origin;
        this.direction = direction;
    }

    public static Ray from(Vec3 origin, Vec3 direction) {
        return new Ray(new Vec3(origin), new Vec3(direction));
    }

    public Vec3 pointAt(float t) {
        return new Vec3(
                origin.x + direction.x * t,
                origin.y + direction.y * t,
                origin.z + direction.z * t
        );
    }

    @Override
    public String toString() {
        return "Ray(origin=" + origin + ", dir=" + direction + ")";
    }

    // --- BuildStrategy ---

    public static void setBuildStrategy(BuildStrategy<Ray> strategy) { buildStrategy = strategy; }
    public static BuildStrategy<Ray> getBuildStrategy() { return buildStrategy; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final Vec3 origin = new Vec3();
        private final Vec3 direction = new Vec3(0f, 0f, -1f);
        private final boolean eager;

        public Builder() { this.eager = false; }
        private Builder(boolean eager) { this.eager = eager; }

        /** Returns a builder that will eagerly cache intermediate results (no-op currently). */
        public static Builder eager() { return new Builder(true); }
        /** Returns a builder that defers all computation to build() time (default). */
        public static Builder lazy() { return new Builder(false); }

        public Builder origin(Vec3 o) { this.origin.set(o); return this; }
        public Builder origin(float x, float y, float z) { this.origin.set(x, y, z); return this; }
        public Builder direction(Vec3 d) { this.direction.set(d); return this; }
        public Builder direction(float x, float y, float z) { this.direction.set(x, y, z); return this; }

        public Ray build() {
            Ray r = buildStrategy.obtain();
            r.origin.set(origin);
            r.direction.set(direction);
            return r;
        }

        public Ray build(BuildStrategy<Ray> strategy) {
            Ray r = strategy.obtain();
            r.origin.set(origin);
            r.direction.set(direction);
            return r;
        }
    }
}
