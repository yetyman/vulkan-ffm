package io.github.yetyman.helpers.math.geometry;

import io.github.yetyman.helpers.math.MathUtil;
import io.github.yetyman.helpers.math.Quaternion;
import io.github.yetyman.helpers.math.Vec3;

/**
 * Static intersection tests between geometric primitives.
 * Methods returning float return the ray parameter t (negative = no hit).
 */
public final class Intersections {

    private Intersections() {}

    /**
     * Ray-Plane intersection. Returns t, or negative if no hit (parallel or behind).
     */
    public static float rayPlane(Ray ray, Plane plane) {
        float denom = plane.nx * ray.direction.x + plane.ny * ray.direction.y + plane.nz * ray.direction.z;
        if (Math.abs(denom) < MathUtil.EPSILON) return -1f;
        float t = -(plane.nx * ray.origin.x + plane.ny * ray.origin.y + plane.nz * ray.origin.z + plane.d) / denom;
        return t >= 0f ? t : -1f;
    }

    /**
     * Ray-AABB intersection (slab method). Returns t, or negative if no hit.
     */
    public static float rayAABB(Ray ray, AABB aabb) {
        float tmin = Float.NEGATIVE_INFINITY;
        float tmax = Float.POSITIVE_INFINITY;

        float ox = ray.origin.x, oy = ray.origin.y, oz = ray.origin.z;
        float dx = ray.direction.x, dy = ray.direction.y, dz = ray.direction.z;

        // X slab
        if (Math.abs(dx) < MathUtil.EPSILON) {
            if (ox < aabb.min.x || ox > aabb.max.x) return -1f;
        } else {
            float inv = 1f / dx;
            float t1 = (aabb.min.x - ox) * inv;
            float t2 = (aabb.max.x - ox) * inv;
            if (t1 > t2) { float tmp = t1; t1 = t2; t2 = tmp; }
            tmin = Math.max(tmin, t1);
            tmax = Math.min(tmax, t2);
            if (tmin > tmax) return -1f;
        }
        // Y slab
        if (Math.abs(dy) < MathUtil.EPSILON) {
            if (oy < aabb.min.y || oy > aabb.max.y) return -1f;
        } else {
            float inv = 1f / dy;
            float t1 = (aabb.min.y - oy) * inv;
            float t2 = (aabb.max.y - oy) * inv;
            if (t1 > t2) { float tmp = t1; t1 = t2; t2 = tmp; }
            tmin = Math.max(tmin, t1);
            tmax = Math.min(tmax, t2);
            if (tmin > tmax) return -1f;
        }
        // Z slab
        if (Math.abs(dz) < MathUtil.EPSILON) {
            if (oz < aabb.min.z || oz > aabb.max.z) return -1f;
        } else {
            float inv = 1f / dz;
            float t1 = (aabb.min.z - oz) * inv;
            float t2 = (aabb.max.z - oz) * inv;
            if (t1 > t2) { float tmp = t1; t1 = t2; t2 = tmp; }
            tmin = Math.max(tmin, t1);
            tmax = Math.min(tmax, t2);
            if (tmin > tmax) return -1f;
        }

        return tmin >= 0f ? tmin : (tmax >= 0f ? tmax : -1f);
    }

    /**
     * Ray-Sphere intersection. Returns nearest t, or negative if no hit.
     */
    public static float raySphere(Ray ray, Sphere sphere) {
        float ocx = ray.origin.x - sphere.center.x;
        float ocy = ray.origin.y - sphere.center.y;
        float ocz = ray.origin.z - sphere.center.z;
        float a = ray.direction.x * ray.direction.x + ray.direction.y * ray.direction.y + ray.direction.z * ray.direction.z;
        float b = 2f * (ocx * ray.direction.x + ocy * ray.direction.y + ocz * ray.direction.z);
        float c = ocx * ocx + ocy * ocy + ocz * ocz - sphere.radius * sphere.radius;
        float disc = b * b - 4f * a * c;
        if (disc < 0f) return -1f;
        float sqrtDisc = (float) Math.sqrt(disc);
        float t0 = (-b - sqrtDisc) / (2f * a);
        if (t0 >= 0f) return t0;
        float t1 = (-b + sqrtDisc) / (2f * a);
        return t1 >= 0f ? t1 : -1f;
    }

    /**
     * Ray-OBB intersection. Returns t, or negative if no hit.
     */
    public static float rayOBB(Ray ray, OBB obb) {
        // Transform ray into OBB local space
        Vec3 localOrigin = ray.origin.subNew(obb.center);
        Vec3 localDir = new Vec3(ray.direction);
        Quaternion inv = obb.orientation.conjugateNew();
        inv.rotateVector(localOrigin);
        inv.rotateVector(localDir);

        AABB localAABB = new AABB(
                new Vec3(-obb.halfExtents.x, -obb.halfExtents.y, -obb.halfExtents.z),
                new Vec3(obb.halfExtents.x, obb.halfExtents.y, obb.halfExtents.z)
        );
        Ray localRay = new Ray(localOrigin, localDir);
        return rayAABB(localRay, localAABB);
    }

    public static ContainmentResult frustumAABB(Frustum frustum, AABB aabb) {
        return frustum.testAABB(aabb);
    }

    public static ContainmentResult frustumSphere(Frustum frustum, Sphere sphere) {
        return frustum.testSphere(sphere);
    }

    public static boolean aabbAABB(AABB a, AABB b) {
        return a.intersects(b);
    }

    public static boolean sphereSphere(Sphere a, Sphere b) {
        return a.intersects(b);
    }

    public static ContainmentResult planeAABB(Plane plane, AABB aabb) {
        float px = plane.nx > 0 ? aabb.max.x : aabb.min.x;
        float py = plane.ny > 0 ? aabb.max.y : aabb.min.y;
        float pz = plane.nz > 0 ? aabb.max.z : aabb.min.z;
        if (plane.nx * px + plane.ny * py + plane.nz * pz + plane.d < 0) {
            return ContainmentResult.OUTSIDE;
        }
        float nx = plane.nx > 0 ? aabb.min.x : aabb.max.x;
        float ny = plane.ny > 0 ? aabb.min.y : aabb.max.y;
        float nz = plane.nz > 0 ? aabb.min.z : aabb.max.z;
        if (plane.nx * nx + plane.ny * ny + plane.nz * nz + plane.d < 0) {
            return ContainmentResult.INTERSECT;
        }
        return ContainmentResult.INSIDE;
    }
}
