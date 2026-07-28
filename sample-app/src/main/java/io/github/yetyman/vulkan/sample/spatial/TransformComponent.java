package io.github.yetyman.vulkan.sample.spatial;

import io.github.yetyman.vulkan.foundation.ecs.*;

import java.util.List;

/**
 * Experimental transform component demonstrating hierarchical transform propagation
 * through the ECS node tree.
 *
 * This is a SAMPLE/EXPERIMENTAL implementation - the spatial subsystem is expected to be
 * replaced as the project matures. It lives in sample-app deliberately to avoid coupling
 * the foundation module to any particular spatial representation.
 *
 * Pattern demonstrated:
 * - Each node with a TransformComponent has a local transform (position, rotation, scale)
 * - World transform is derived by composing with the parent's world transform
 * - Uses NEAREST_ANCESTOR lookup to find the parent TransformComponent
 * - Dirty flag optimization: world transform is only recomputed when local or parent changed
 *
 * Uses float arrays internally for GPU-upload friendliness (column-major 4x4 matrix).
 */
public class TransformComponent implements Component {

    // Local transform (TRS decomposition)
    private float posX, posY, posZ;
    private float rotX, rotY, rotZ, rotW = 1.0f; // quaternion, identity by default
    private float scaleX = 1.0f, scaleY = 1.0f, scaleZ = 1.0f;

    // Cached matrices
    private final float[] localMatrix = new float[16];
    private final float[] worldMatrix = new float[16];
    private boolean localDirty = true;
    private boolean worldDirty = true;

    // Parent transform (resolved via DI)
    private TransformComponent parentTransform;

    // Change tracking for observers
    private long version = 0;
    private long parentVersionSnapshot = -1;

    // The node this component is attached to
    private Node node;

    public TransformComponent() {
        setIdentity(localMatrix);
        setIdentity(worldMatrix);
    }

    // --- Component lifecycle ---

    @Override
    public List<Dependency<?>> requires() {
        return List.of(
                new Dependency<>(TransformComponent.class, ClaimStyle.PERMISSIVE,
                        LookupScope.NEAREST_ANCESTOR, FallbackPolicy.optional())
        );
    }

    @Override
    public void onInit(Node node) {
        this.node = node;
    }

    @Override
    public void resolveDependencies(Node node) {
        // Find parent transform via ancestor lookup
        Node current = node.parent();
        while (current != null) {
            TransformComponent ancestor = current.findComponent(TransformComponent.class);
            if (ancestor != null) {
                this.parentTransform = ancestor;
                this.parentVersionSnapshot = -1; // force recalc
                this.worldDirty = true;
                return;
            }
            current = current.parent();
        }
        this.parentTransform = null;
        this.worldDirty = true;
    }

    // --- Position ---

    public float posX() { return posX; }
    public float posY() { return posY; }
    public float posZ() { return posZ; }

    public TransformComponent setPosition(float x, float y, float z) {
        this.posX = x;
        this.posY = y;
        this.posZ = z;
        markLocalDirty();
        return this;
    }

    public TransformComponent translate(float dx, float dy, float dz) {
        this.posX += dx;
        this.posY += dy;
        this.posZ += dz;
        markLocalDirty();
        return this;
    }

    // --- Rotation (quaternion) ---

    public float rotX() { return rotX; }
    public float rotY() { return rotY; }
    public float rotZ() { return rotZ; }
    public float rotW() { return rotW; }

    public TransformComponent setRotation(float x, float y, float z, float w) {
        this.rotX = x;
        this.rotY = y;
        this.rotZ = z;
        this.rotW = w;
        markLocalDirty();
        return this;
    }

    /**
     * Sets rotation from Euler angles (radians).
     */
    public TransformComponent setRotationEuler(float pitch, float yaw, float roll) {
        float cp = (float) Math.cos(pitch * 0.5f);
        float sp = (float) Math.sin(pitch * 0.5f);
        float cy = (float) Math.cos(yaw * 0.5f);
        float sy = (float) Math.sin(yaw * 0.5f);
        float cr = (float) Math.cos(roll * 0.5f);
        float sr = (float) Math.sin(roll * 0.5f);

        this.rotW = cr * cp * cy + sr * sp * sy;
        this.rotX = sr * cp * cy - cr * sp * sy;
        this.rotY = cr * sp * cy + sr * cp * sy;
        this.rotZ = cr * cp * sy - sr * sp * cy;
        markLocalDirty();
        return this;
    }

    /**
     * Sets rotation from axis-angle (axis must be normalized).
     */
    public TransformComponent setRotationAxisAngle(float ax, float ay, float az, float angle) {
        float halfAngle = angle * 0.5f;
        float s = (float) Math.sin(halfAngle);
        this.rotX = ax * s;
        this.rotY = ay * s;
        this.rotZ = az * s;
        this.rotW = (float) Math.cos(halfAngle);
        markLocalDirty();
        return this;
    }

    // --- Scale ---

    public float scaleX() { return scaleX; }
    public float scaleY() { return scaleY; }
    public float scaleZ() { return scaleZ; }

    public TransformComponent setScale(float x, float y, float z) {
        this.scaleX = x;
        this.scaleY = y;
        this.scaleZ = z;
        markLocalDirty();
        return this;
    }

    public TransformComponent setScale(float uniform) {
        return setScale(uniform, uniform, uniform);
    }

    // --- Matrix access ---

    /**
     * @return the local transform matrix (4x4, column-major).
     * Recomputed lazily on access if dirty.
     */
    public float[] localMatrix() {
        if (localDirty) {
            recomputeLocalMatrix();
        }
        return localMatrix;
    }

    /**
     * @return the world transform matrix (4x4, column-major).
     * Recomputed lazily on access if dirty or parent has changed.
     */
    public float[] worldMatrix() {
        if (isWorldDirty()) {
            recomputeWorldMatrix();
        }
        return worldMatrix;
    }

    /**
     * @return a monotonically increasing version counter that increments on any change.
     * Used by children to detect when their parent has changed.
     */
    public long version() { return version; }

    /**
     * @return true if the world matrix needs recomputation.
     */
    public boolean isWorldDirty() {
        if (worldDirty) return true;
        if (parentTransform != null && parentTransform.version() != parentVersionSnapshot) {
            return true;
        }
        return false;
    }

    // --- Internal ---

    private void markLocalDirty() {
        localDirty = true;
        worldDirty = true;
        version++;
    }

    private void recomputeLocalMatrix() {
        // Build TRS matrix: M = T * R * S (quaternion to rotation matrix)
        float x2 = rotX + rotX, y2 = rotY + rotY, z2 = rotZ + rotZ;
        float xx = rotX * x2, xy = rotX * y2, xz = rotX * z2;
        float yy = rotY * y2, yz = rotY * z2, zz = rotZ * z2;
        float wx = rotW * x2, wy = rotW * y2, wz = rotW * z2;

        // Column 0
        localMatrix[0] = (1.0f - (yy + zz)) * scaleX;
        localMatrix[1] = (xy + wz) * scaleX;
        localMatrix[2] = (xz - wy) * scaleX;
        localMatrix[3] = 0.0f;

        // Column 1
        localMatrix[4] = (xy - wz) * scaleY;
        localMatrix[5] = (1.0f - (xx + zz)) * scaleY;
        localMatrix[6] = (yz + wx) * scaleY;
        localMatrix[7] = 0.0f;

        // Column 2
        localMatrix[8] = (xz + wy) * scaleZ;
        localMatrix[9] = (yz - wx) * scaleZ;
        localMatrix[10] = (1.0f - (xx + yy)) * scaleZ;
        localMatrix[11] = 0.0f;

        // Column 3 (translation)
        localMatrix[12] = posX;
        localMatrix[13] = posY;
        localMatrix[14] = posZ;
        localMatrix[15] = 1.0f;

        localDirty = false;
    }

    private void recomputeWorldMatrix() {
        if (localDirty) {
            recomputeLocalMatrix();
        }

        if (parentTransform == null) {
            // No parent - world = local
            System.arraycopy(localMatrix, 0, worldMatrix, 0, 16);
        } else {
            // world = parent.world * local
            float[] parentWorld = parentTransform.worldMatrix();
            multiply4x4(parentWorld, localMatrix, worldMatrix);
            parentVersionSnapshot = parentTransform.version();
        }

        worldDirty = false;
    }

    private static void setIdentity(float[] m) {
        m[0] = 1; m[1] = 0; m[2] = 0; m[3] = 0;
        m[4] = 0; m[5] = 1; m[6] = 0; m[7] = 0;
        m[8] = 0; m[9] = 0; m[10] = 1; m[11] = 0;
        m[12] = 0; m[13] = 0; m[14] = 0; m[15] = 1;
    }

    /**
     * Multiplies two 4x4 column-major matrices: result = a * b.
     */
    private static void multiply4x4(float[] a, float[] b, float[] result) {
        for (int col = 0; col < 4; col++) {
            for (int row = 0; row < 4; row++) {
                float sum = 0;
                for (int k = 0; k < 4; k++) {
                    sum += a[row + k * 4] * b[k + col * 4];
                }
                result[row + col * 4] = sum;
            }
        }
    }

    @Override
    public String toString() {
        return "TransformComponent{pos=(" + posX + "," + posY + "," + posZ +
                "), scale=(" + scaleX + "," + scaleY + "," + scaleZ + ")}";
    }
}
