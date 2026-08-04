package io.github.yetyman.helpers.math.geometry;

import io.github.yetyman.helpers.math.BuildStrategy;
import io.github.yetyman.helpers.math.Quaternion;
import io.github.yetyman.helpers.math.Vec3;

/**
 * Oriented bounding box defined by center, half-extents, and orientation.
 */
public class OBB {

    private static BuildStrategy<OBB> buildStrategy = BuildStrategy.allocating(OBB::new);

    public final Vec3 center;
    public final Vec3 halfExtents;
    public final Quaternion orientation;

    public OBB() {
        this.center = new Vec3();
        this.halfExtents = new Vec3();
        this.orientation = new Quaternion();
    }

    public OBB(Vec3 center, Vec3 halfExtents, Quaternion orientation) {
        this.center = center;
        this.halfExtents = halfExtents;
        this.orientation = orientation;
    }

    public boolean contains(Vec3 point) {
        Vec3 local = point.subNew(center);
        Quaternion inv = orientation.conjugateNew();
        inv.rotateVector(local);
        return Math.abs(local.x) <= halfExtents.x
            && Math.abs(local.y) <= halfExtents.y
            && Math.abs(local.z) <= halfExtents.z;
    }

    public AABB toAABB() {
        Vec3 axisX = new Vec3(1, 0, 0);
        Vec3 axisY = new Vec3(0, 1, 0);
        Vec3 axisZ = new Vec3(0, 0, 1);
        orientation.rotateVector(axisX);
        orientation.rotateVector(axisY);
        orientation.rotateVector(axisZ);

        float ex = Math.abs(axisX.x) * halfExtents.x + Math.abs(axisY.x) * halfExtents.y + Math.abs(axisZ.x) * halfExtents.z;
        float ey = Math.abs(axisX.y) * halfExtents.x + Math.abs(axisY.y) * halfExtents.y + Math.abs(axisZ.y) * halfExtents.z;
        float ez = Math.abs(axisX.z) * halfExtents.x + Math.abs(axisY.z) * halfExtents.y + Math.abs(axisZ.z) * halfExtents.z;

        Vec3 ext = new Vec3(ex, ey, ez);
        return new AABB(center.subNew(ext), center.addNew(ext));
    }

    @Override
    public String toString() {
        return "OBB(center=" + center + ", half=" + halfExtents + ", orient=" + orientation + ")";
    }

    // --- BuildStrategy ---

    public static void setBuildStrategy(BuildStrategy<OBB> strategy) { buildStrategy = strategy; }
    public static BuildStrategy<OBB> getBuildStrategy() { return buildStrategy; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final Vec3 center = new Vec3();
        private final Vec3 halfExtents = new Vec3();
        private final Quaternion orientation = new Quaternion();
        private final boolean eager;

        public Builder() { this.eager = false; }
        private Builder(boolean eager) { this.eager = eager; }

        /** Returns a builder that will eagerly cache intermediate results (no-op currently). */
        public static Builder eager() { return new Builder(true); }
        /** Returns a builder that defers all computation to build() time (default). */
        public static Builder lazy() { return new Builder(false); }

        public Builder center(Vec3 c) { this.center.set(c); return this; }
        public Builder center(float x, float y, float z) { this.center.set(x, y, z); return this; }
        public Builder halfExtents(Vec3 h) { this.halfExtents.set(h); return this; }
        public Builder halfExtents(float x, float y, float z) { this.halfExtents.set(x, y, z); return this; }
        public Builder orientation(Quaternion q) { this.orientation.set(q); return this; }

        public OBB build() {
            OBB o = buildStrategy.obtain();
            o.center.set(center);
            o.halfExtents.set(halfExtents);
            o.orientation.set(orientation);
            return o;
        }

        public OBB build(BuildStrategy<OBB> strategy) {
            OBB o = strategy.obtain();
            o.center.set(center);
            o.halfExtents.set(halfExtents);
            o.orientation.set(orientation);
            return o;
        }
    }
}
