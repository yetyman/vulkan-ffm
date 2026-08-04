package io.github.yetyman.helpers.math;

/**
 * Composition of position + rotation + scale with lazy matrix computation and optional parent hierarchy.
 */
public class Transform {

    private static BuildStrategy<Transform> buildStrategy = BuildStrategy.allocating(Transform::new);

    private final Vec3 position = new Vec3(0f, 0f, 0f);
    private final Quaternion rotation = new Quaternion();
    private final Vec3 scale = new Vec3(1f, 1f, 1f);

    private final Mat4 localMatrix = new Mat4();
    private boolean localDirty = true;

    private Transform parent;
    private final Mat4 worldMatrix = new Mat4();
    private boolean worldDirty = true;

    public Transform() {}

    // --- Accessors ---

    public Vec3 position() { return position; }
    public Quaternion rotation() { return rotation; }
    public Vec3 scale() { return scale; }
    public Transform parent() { return parent; }

    // --- Setters (mark dirty) ---

    public Transform setPosition(Vec3 pos) {
        position.set(pos);
        markDirty();
        return this;
    }

    public Transform setPosition(float x, float y, float z) {
        position.set(x, y, z);
        markDirty();
        return this;
    }

    public Transform setRotation(Quaternion rot) {
        rotation.set(rot);
        markDirty();
        return this;
    }

    public Transform setScale(Vec3 scale) {
        this.scale.set(scale);
        markDirty();
        return this;
    }

    public Transform setScale(float uniform) {
        this.scale.set(uniform, uniform, uniform);
        markDirty();
        return this;
    }

    /**
     * Sets the local matrix directly, decomposing it into position/rotation/scale.
     * No affine check is performed.
     */
    public Transform setLocalMatrix(Mat4 matrix) {
        localMatrix.set(matrix);
        matrix.decompose(position, rotation, scale);
        localDirty = false;
        worldDirty = true;
        return this;
    }

    public Transform translate(Vec3 delta) {
        position.add(delta);
        markDirty();
        return this;
    }

    public Transform translate(float dx, float dy, float dz) {
        position.add(dx, dy, dz);
        markDirty();
        return this;
    }

    public Transform rotate(Quaternion delta) {
        rotation.multiply(delta);
        markDirty();
        return this;
    }

    public Transform scaleBy(Vec3 factor) {
        scale.mul(factor);
        markDirty();
        return this;
    }

    public Transform scaleBy(float factor) {
        scale.mul(factor);
        markDirty();
        return this;
    }

    public Transform setParent(Transform parent) {
        this.parent = parent;
        worldDirty = true;
        return this;
    }

    // --- Matrix computation ---

    /**
     * Returns the local TRS matrix, recomputing only when dirty.
     */
    public Mat4 localMatrix() {
        if (localDirty) {
            Mat4 rot = rotation.toMat4();
            localMatrix.set(rot);
            localMatrix.m00 *= scale.x; localMatrix.m01 *= scale.x; localMatrix.m02 *= scale.x;
            localMatrix.m10 *= scale.y; localMatrix.m11 *= scale.y; localMatrix.m12 *= scale.y;
            localMatrix.m20 *= scale.z; localMatrix.m21 *= scale.z; localMatrix.m22 *= scale.z;
            localMatrix.m30 = position.x;
            localMatrix.m31 = position.y;
            localMatrix.m32 = position.z;
            localMatrix.m03 = 0f; localMatrix.m13 = 0f; localMatrix.m23 = 0f; localMatrix.m33 = 1f;
            localDirty = false;
            worldDirty = true;
        }
        return localMatrix;
    }

    /**
     * Returns the world matrix (parent.worldMatrix * localMatrix), recomputing only when dirty.
     */
    public Mat4 worldMatrix() {
        if (worldDirty || localDirty) {
            Mat4 local = localMatrix();
            if (parent != null) {
                Mat4 pw = parent.worldMatrix();
                worldMatrix.set(pw).mul(local);
            } else {
                worldMatrix.set(local);
            }
            worldDirty = false;
        }
        return worldMatrix;
    }

    /**
     * Forces recalculation of local and world matrices on next access.
     */
    public void markDirty() {
        localDirty = true;
        worldDirty = true;
    }

    public boolean isDirty() {
        return localDirty || worldDirty;
    }

    // --- Static utility ---

    public static void decompose(Mat4 matrix, Vec3 outPos, Quaternion outRot, Vec3 outScale) {
        matrix.decompose(outPos, outRot, outScale);
    }

    @Override
    public String toString() {
        return "Transform(pos=" + position + ", rot=" + rotation + ", scale=" + scale + ")";
    }

    // --- BuildStrategy ---

    public static void setBuildStrategy(BuildStrategy<Transform> strategy) {
        buildStrategy = strategy;
    }

    public static BuildStrategy<Transform> getBuildStrategy() {
        return buildStrategy;
    }

    // --- Builder ---

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final Vec3 position = new Vec3(0f, 0f, 0f);
        private final Quaternion rotation = new Quaternion();
        private final Vec3 scale = new Vec3(1f, 1f, 1f);
        private Mat4 localMatrix;
        private Mat4 worldMatrix;
        private Transform parent;
        private final boolean eager;

        public Builder() { this.eager = false; }
        private Builder(boolean eager) { this.eager = eager; }

        /** Returns a builder that will eagerly cache intermediate results (no-op currently). */
        public static Builder eager() { return new Builder(true); }
        /** Returns a builder that defers all computation to build() time (default). */
        public static Builder lazy() { return new Builder(false); }

        public Builder position(Vec3 pos) { this.position.set(pos); return this; }
        public Builder position(float x, float y, float z) { this.position.set(x, y, z); return this; }
        public Builder rotation(Quaternion rot) { this.rotation.set(rot); return this; }
        public Builder scale(Vec3 scale) { this.scale.set(scale); return this; }
        public Builder scale(float uniform) { this.scale.set(uniform, uniform, uniform); return this; }
        public Builder localMatrix(Mat4 matrix) { this.localMatrix = matrix; return this; }
        public Builder worldMatrix(Mat4 matrix) { this.worldMatrix = matrix; return this; }
        public Builder parent(Transform parent) { this.parent = parent; return this; }

        public Transform build() {
            Transform t = buildStrategy.obtain();
            return apply(t);
        }

        public Transform build(BuildStrategy<Transform> strategy) {
            Transform t = strategy.obtain();
            return apply(t);
        }

        // NOTE: apply() could be invoked eagerly to cache intermediate results when parameters change,
        // but is not yet implemented that way. Currently all computation happens at build() time.
        private Transform apply(Transform t) {
            if (localMatrix != null) {
                t.setLocalMatrix(localMatrix);
            } else {
                t.position.set(position);
                t.rotation.set(rotation);
                t.scale.set(scale);
                t.markDirty();
            }
            if (parent != null) {
                t.setParent(parent);
            }
            if (worldMatrix != null) {
                t.worldMatrix.set(worldMatrix);
                t.worldDirty = false;
            }
            return t;
        }
    }
}
