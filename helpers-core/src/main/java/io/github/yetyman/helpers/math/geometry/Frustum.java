package io.github.yetyman.helpers.math.geometry;

import io.github.yetyman.helpers.math.BuildStrategy;
import io.github.yetyman.helpers.math.Mat4;
import io.github.yetyman.helpers.math.Vec3;

/**
 * View frustum defined by 6 planes extracted from a view-projection matrix.
 * Planes are tested in rejection-likely order: near, far, left, right, top, bottom.
 */
public class Frustum {

    public final Plane near = new Plane();
    public final Plane far = new Plane();
    public final Plane left = new Plane();
    public final Plane right = new Plane();
    public final Plane top = new Plane();
    public final Plane bottom = new Plane();

    private final Plane[] planes = {near, far, left, right, top, bottom};

    public Frustum() {}

    /**
     * Extracts frustum planes from a combined view-projection matrix.
     */
    public static Frustum fromViewProjection(Mat4 vp) {
        Frustum f = new Frustum();
        // Near: row3 + row2
        f.near.nx = vp.m03 + vp.m02; f.near.ny = vp.m13 + vp.m12; f.near.nz = vp.m23 + vp.m22; f.near.d = vp.m33 + vp.m32;
        // Far: row3 - row2
        f.far.nx = vp.m03 - vp.m02; f.far.ny = vp.m13 - vp.m12; f.far.nz = vp.m23 - vp.m22; f.far.d = vp.m33 - vp.m32;
        // Left: row3 + row0
        f.left.nx = vp.m03 + vp.m00; f.left.ny = vp.m13 + vp.m10; f.left.nz = vp.m23 + vp.m20; f.left.d = vp.m33 + vp.m30;
        // Right: row3 - row0
        f.right.nx = vp.m03 - vp.m00; f.right.ny = vp.m13 - vp.m10; f.right.nz = vp.m23 - vp.m20; f.right.d = vp.m33 - vp.m30;
        // Top: row3 - row1
        f.top.nx = vp.m03 - vp.m01; f.top.ny = vp.m13 - vp.m11; f.top.nz = vp.m23 - vp.m21; f.top.d = vp.m33 - vp.m31;
        // Bottom: row3 + row1
        f.bottom.nx = vp.m03 + vp.m01; f.bottom.ny = vp.m13 + vp.m11; f.bottom.nz = vp.m23 + vp.m21; f.bottom.d = vp.m33 + vp.m31;
        // Normalize all planes
        for (Plane p : f.planes) p.normalize();
        return f;
    }

    public ContainmentResult testAABB(AABB aabb) {
        boolean allInside = true;
        for (Plane p : planes) {
            float px = p.nx > 0 ? aabb.max.x : aabb.min.x;
            float py = p.ny > 0 ? aabb.max.y : aabb.min.y;
            float pz = p.nz > 0 ? aabb.max.z : aabb.min.z;
            if (p.nx * px + p.ny * py + p.nz * pz + p.d < 0) {
                return ContainmentResult.OUTSIDE;
            }
            float nx = p.nx > 0 ? aabb.min.x : aabb.max.x;
            float ny = p.ny > 0 ? aabb.min.y : aabb.max.y;
            float nz = p.nz > 0 ? aabb.min.z : aabb.max.z;
            if (p.nx * nx + p.ny * ny + p.nz * nz + p.d < 0) {
                allInside = false;
            }
        }
        return allInside ? ContainmentResult.INSIDE : ContainmentResult.INTERSECT;
    }

    public ContainmentResult testSphere(Sphere sphere) {
        boolean allInside = true;
        for (Plane p : planes) {
            float dist = p.nx * sphere.center.x + p.ny * sphere.center.y + p.nz * sphere.center.z + p.d;
            if (dist < -sphere.radius) return ContainmentResult.OUTSIDE;
            if (dist < sphere.radius) allInside = false;
        }
        return allInside ? ContainmentResult.INSIDE : ContainmentResult.INTERSECT;
    }

    public boolean testPoint(Vec3 point) {
        for (Plane p : planes) {
            if (p.nx * point.x + p.ny * point.y + p.nz * point.z + p.d < 0) {
                return false;
            }
        }
        return true;
    }

    // --- BuildStrategy ---

    private static BuildStrategy<Frustum> buildStrategy = BuildStrategy.allocating(Frustum::new);

    public static void setBuildStrategy(BuildStrategy<Frustum> strategy) { buildStrategy = strategy; }
    public static BuildStrategy<Frustum> getBuildStrategy() { return buildStrategy; }

    public static FrustumBuilder builder() { return new FrustumBuilder(); }

    public static class FrustumBuilder {
        private Mat4 viewProjection;
        private final boolean eager;

        public FrustumBuilder() { this.eager = false; }
        private FrustumBuilder(boolean eager) { this.eager = eager; }

        /** Returns a builder that will eagerly cache intermediate results (no-op currently). */
        public static FrustumBuilder eager() { return new FrustumBuilder(true); }
        /** Returns a builder that defers all computation to build() time (default). */
        public static FrustumBuilder lazy() { return new FrustumBuilder(false); }

        public FrustumBuilder fromViewProjection(Mat4 vp) {
            this.viewProjection = vp;
            return this;
        }

        public Frustum build() {
            return apply(buildStrategy.obtain());
        }

        public Frustum build(BuildStrategy<Frustum> strategy) {
            return apply(strategy.obtain());
        }

        // NOTE: apply() could be invoked eagerly to cache intermediate results when parameters change,
        // but is not yet implemented that way. Currently all computation happens at build() time.
        private Frustum apply(Frustum f) {
            if (viewProjection != null) {
                Mat4 vp = viewProjection;
                f.near.nx = vp.m03 + vp.m02; f.near.ny = vp.m13 + vp.m12; f.near.nz = vp.m23 + vp.m22; f.near.d = vp.m33 + vp.m32;
                f.far.nx = vp.m03 - vp.m02; f.far.ny = vp.m13 - vp.m12; f.far.nz = vp.m23 - vp.m22; f.far.d = vp.m33 - vp.m32;
                f.left.nx = vp.m03 + vp.m00; f.left.ny = vp.m13 + vp.m10; f.left.nz = vp.m23 + vp.m20; f.left.d = vp.m33 + vp.m30;
                f.right.nx = vp.m03 - vp.m00; f.right.ny = vp.m13 - vp.m10; f.right.nz = vp.m23 - vp.m20; f.right.d = vp.m33 - vp.m30;
                f.top.nx = vp.m03 - vp.m01; f.top.ny = vp.m13 - vp.m11; f.top.nz = vp.m23 - vp.m21; f.top.d = vp.m33 - vp.m31;
                f.bottom.nx = vp.m03 + vp.m01; f.bottom.ny = vp.m13 + vp.m11; f.bottom.nz = vp.m23 + vp.m21; f.bottom.d = vp.m33 + vp.m31;
                for (Plane p : f.planes) p.normalize();
            }
            return f;
        }
    }
}
