package io.github.yetyman.helpers.math;

import io.github.yetyman.vulkan.buffers.BufferWritable;
import io.github.yetyman.vulkan.buffers.GpuLayout;

import java.nio.ByteBuffer;

/**
 * Dual quaternion encoding rotation + translation as q_real + epsilon * q_dual.
 * Used for rigid body interpolation and skeletal animation blending without candy-wrapper artifacts.
 * Non-uniform scale cannot be encoded — handle scale separately.
 *
 * Convention: real part is the rotation, dual part encodes translation.
 * Identity = real(0,0,0,1) + dual(0,0,0,0).
 */
public class DualQuaternion implements BufferWritable {

    // --- GPU Layouts ---

    /** Default layout: real(x,y,z,w) then dual(x,y,z,w) (32 bytes). */
    public static final GpuLayout<DualQuaternion> REAL_DUAL = new GpuLayout<>() {
        @Override public int byteSize() { return 32; }
        @Override public void writeTo(DualQuaternion dq, ByteBuffer buf) {
            buf.putFloat(dq.rx); buf.putFloat(dq.ry); buf.putFloat(dq.rz); buf.putFloat(dq.rw);
            buf.putFloat(dq.dx); buf.putFloat(dq.dy); buf.putFloat(dq.dz); buf.putFloat(dq.dw);
        }
        @Override public void readFrom(DualQuaternion dq, ByteBuffer buf) {
            dq.rx = buf.getFloat(); dq.ry = buf.getFloat(); dq.rz = buf.getFloat(); dq.rw = buf.getFloat();
            dq.dx = buf.getFloat(); dq.dy = buf.getFloat(); dq.dz = buf.getFloat(); dq.dw = buf.getFloat();
        }
    };

    /** Default layout used by BufferWritable methods. */
    public static final GpuLayout<DualQuaternion> DEFAULT_LAYOUT = REAL_DUAL;

    private static BuildStrategy<DualQuaternion> buildStrategy = BuildStrategy.allocating(DualQuaternion::new);

    // Real part (rotation)
    public float rx, ry, rz, rw;
    // Dual part (translation encoding)
    public float dx, dy, dz, dw;

    /** Creates an identity dual quaternion. */
    public DualQuaternion() {
        rx = 0f; ry = 0f; rz = 0f; rw = 1f;
        dx = 0f; dy = 0f; dz = 0f; dw = 0f;
    }

    public DualQuaternion(float rx, float ry, float rz, float rw,
                          float dx, float dy, float dz, float dw) {
        this.rx = rx; this.ry = ry; this.rz = rz; this.rw = rw;
        this.dx = dx; this.dy = dy; this.dz = dz; this.dw = dw;
    }

    public DualQuaternion(DualQuaternion other) {
        this.rx = other.rx; this.ry = other.ry; this.rz = other.rz; this.rw = other.rw;
        this.dx = other.dx; this.dy = other.dy; this.dz = other.dz; this.dw = other.dw;
    }

    // --- Static factories ---

    public static DualQuaternion identity() {
        return new DualQuaternion();
    }

    /**
     * Creates a dual quaternion from a rotation quaternion and translation vector.
     * dual = 0.5 * translation_quat * rotation
     * where translation_quat = (tx, ty, tz, 0)
     */
    public static DualQuaternion fromRotationTranslation(Quaternion rotation, Vec3 translation) {
        DualQuaternion dq = new DualQuaternion();
        dq.rx = rotation.x;
        dq.ry = rotation.y;
        dq.rz = rotation.z;
        dq.rw = rotation.w;

        // dual = 0.5 * (0, t) * r
        // where (0, t) is a pure quaternion (tx, ty, tz, 0)
        float tx = translation.x, ty = translation.y, tz = translation.z;
        dq.dx = 0.5f * ( tx * rotation.w + ty * rotation.z - tz * rotation.y);
        dq.dy = 0.5f * (-tx * rotation.z + ty * rotation.w + tz * rotation.x);
        dq.dz = 0.5f * ( tx * rotation.y - ty * rotation.x + tz * rotation.w);
        dq.dw = 0.5f * (-tx * rotation.x - ty * rotation.y - tz * rotation.z);
        return dq;
    }

    /**
     * Creates a dual quaternion from a Transform (uses position + rotation, ignores scale).
     */
    public static DualQuaternion fromTransform(Transform transform) {
        return fromRotationTranslation(transform.rotation(), transform.position());
    }

    /**
     * Creates a dual quaternion representing pure translation (no rotation).
     */
    public static DualQuaternion fromTranslation(Vec3 translation) {
        DualQuaternion dq = new DualQuaternion();
        dq.dx = 0.5f * translation.x;
        dq.dy = 0.5f * translation.y;
        dq.dz = 0.5f * translation.z;
        return dq;
    }

    /**
     * Creates a dual quaternion representing pure rotation (no translation).
     */
    public static DualQuaternion fromRotation(Quaternion rotation) {
        DualQuaternion dq = new DualQuaternion();
        dq.rx = rotation.x;
        dq.ry = rotation.y;
        dq.rz = rotation.z;
        dq.rw = rotation.w;
        return dq;
    }

    // --- Setters ---

    public DualQuaternion set(DualQuaternion other) {
        rx = other.rx; ry = other.ry; rz = other.rz; rw = other.rw;
        dx = other.dx; dy = other.dy; dz = other.dz; dw = other.dw;
        return this;
    }

    public DualQuaternion set(Quaternion rotation, Vec3 translation) {
        rx = rotation.x; ry = rotation.y; rz = rotation.z; rw = rotation.w;
        float tx = translation.x, ty = translation.y, tz = translation.z;
        dx = 0.5f * ( tx * rw + ty * rz - tz * ry);
        dy = 0.5f * (-tx * rz + ty * rw + tz * rx);
        dz = 0.5f * ( tx * ry - ty * rx + tz * rw);
        dw = 0.5f * (-tx * rx - ty * ry - tz * rz);
        return this;
    }

    public DualQuaternion setIdentity() {
        rx = 0f; ry = 0f; rz = 0f; rw = 1f;
        dx = 0f; dy = 0f; dz = 0f; dw = 0f;
        return this;
    }

    // --- Operations ---

    /**
     * Multiplies this dual quaternion by other: this = this * other.
     * Represents composing the two rigid transforms.
     */
    public DualQuaternion multiply(DualQuaternion other) {
        // Real part: r1 * r2
        float nrx = rw*other.rx + rx*other.rw + ry*other.rz - rz*other.ry;
        float nry = rw*other.ry - rx*other.rz + ry*other.rw + rz*other.rx;
        float nrz = rw*other.rz + rx*other.ry - ry*other.rx + rz*other.rw;
        float nrw = rw*other.rw - rx*other.rx - ry*other.ry - rz*other.rz;

        // Dual part: r1*d2 + d1*r2
        float ndx = rw*other.dx + rx*other.dw + ry*other.dz - rz*other.dy
                  + dw*other.rx + dx*other.rw + dy*other.rz - dz*other.ry;
        float ndy = rw*other.dy - rx*other.dz + ry*other.dw + rz*other.dx
                  + dw*other.ry - dx*other.rz + dy*other.rw + dz*other.rx;
        float ndz = rw*other.dz + rx*other.dy - ry*other.dx + rz*other.dw
                  + dw*other.rz + dx*other.ry - dy*other.rx + dz*other.rw;
        float ndw = rw*other.dw - rx*other.dx - ry*other.dy - rz*other.dz
                  + dw*other.rw - dx*other.rx - dy*other.ry - dz*other.rz;

        rx = nrx; ry = nry; rz = nrz; rw = nrw;
        dx = ndx; dy = ndy; dz = ndz; dw = ndw;
        return this;
    }

    public DualQuaternion multiplyNew(DualQuaternion other) {
        return new DualQuaternion(this).multiply(other);
    }

    /**
     * Normalizes this dual quaternion so the real part has unit length
     * and the dual part is orthogonal to the real part.
     */
    public DualQuaternion normalize() {
        float realLen = (float) Math.sqrt(rx*rx + ry*ry + rz*rz + rw*rw);
        if (realLen < MathUtil.EPSILON) {
            setIdentity();
            return this;
        }
        float invLen = 1f / realLen;
        rx *= invLen; ry *= invLen; rz *= invLen; rw *= invLen;
        dx *= invLen; dy *= invLen; dz *= invLen; dw *= invLen;

        // Ensure orthogonality: dual -= dot(real, dual) * real
        float dot = rx*dx + ry*dy + rz*dz + rw*dw;
        dx -= dot * rx;
        dy -= dot * ry;
        dz -= dot * rz;
        dw -= dot * rw;
        return this;
    }

    public DualQuaternion normalizeNew() {
        return new DualQuaternion(this).normalize();
    }

    /**
     * Conjugate of the dual quaternion (inverse for unit dual quaternions).
     */
    public DualQuaternion conjugate() {
        rx = -rx; ry = -ry; rz = -rz;
        dx = -dx; dy = -dy; dz = -dz;
        return this;
    }

    public DualQuaternion conjugateNew() {
        return new DualQuaternion(this).conjugate();
    }

    /**
     * Blends this dual quaternion with another using DLB (Dual quaternion Linear Blending).
     * Used for skinning: accumulate weighted bone transforms then normalize.
     * this = this * (1-t) + other * t, then normalize.
     */
    public DualQuaternion blend(DualQuaternion other, float t) {
        float oneMinusT = 1f - t;

        // Ensure shortest path (flip if dot of real parts is negative)
        float dot = rx*other.rx + ry*other.ry + rz*other.rz + rw*other.rw;
        float sign = dot < 0f ? -1f : 1f;

        rx = oneMinusT * rx + t * sign * other.rx;
        ry = oneMinusT * ry + t * sign * other.ry;
        rz = oneMinusT * rz + t * sign * other.rz;
        rw = oneMinusT * rw + t * sign * other.rw;
        dx = oneMinusT * dx + t * sign * other.dx;
        dy = oneMinusT * dy + t * sign * other.dy;
        dz = oneMinusT * dz + t * sign * other.dz;
        dw = oneMinusT * dw + t * sign * other.dw;

        normalize();
        return this;
    }

    public DualQuaternion blendNew(DualQuaternion other, float t) {
        return new DualQuaternion(this).blend(other, t);
    }

    /**
     * Accumulates a weighted dual quaternion (for multi-bone skinning).
     * Does NOT normalize — caller must normalize after all weights are accumulated.
     */
    public DualQuaternion accumulate(DualQuaternion other, float weight) {
        // Ensure shortest path
        float dot = rx*other.rx + ry*other.ry + rz*other.rz + rw*other.rw;
        float sign = dot < 0f ? -1f : 1f;
        float w = weight * sign;

        rx += w * other.rx;
        ry += w * other.ry;
        rz += w * other.rz;
        rw += w * other.rw;
        dx += w * other.dx;
        dy += w * other.dy;
        dz += w * other.dz;
        dw += w * other.dw;
        return this;
    }

    // --- Conversion ---

    /**
     * Converts to a 4x4 transformation matrix.
     */
    public Mat4 toMat4() {
        // Ensure normalized
        float len = (float) Math.sqrt(rx*rx + ry*ry + rz*rz + rw*rw);
        float nrx = rx, nry = ry, nrz = rz, nrw = rw;
        float ndx = dx, ndy = dy, ndz = dz, ndw = dw;
        if (len > MathUtil.EPSILON) {
            float inv = 1f / len;
            nrx *= inv; nry *= inv; nrz *= inv; nrw *= inv;
            ndx *= inv; ndy *= inv; ndz *= inv; ndw *= inv;
        }

        // Rotation from real part
        Mat4 m = new Mat4();
        float xx = nrx*nrx, yy = nry*nry, zz = nrz*nrz;
        float xy = nrx*nry, xz = nrx*nrz, yz = nry*nrz;
        float wx = nrw*nrx, wy = nrw*nry, wz = nrw*nrz;

        m.m00 = 1f - 2f*(yy + zz); m.m01 = 2f*(xy + wz);       m.m02 = 2f*(xz - wy);       m.m03 = 0f;
        m.m10 = 2f*(xy - wz);       m.m11 = 1f - 2f*(xx + zz); m.m12 = 2f*(yz + wx);       m.m13 = 0f;
        m.m20 = 2f*(xz + wy);       m.m21 = 2f*(yz - wx);       m.m22 = 1f - 2f*(xx + yy); m.m23 = 0f;

        // Translation from dual part: t = 2 * dual * conj(real)
        m.m30 = 2f * (-ndw*nrx + ndx*nrw - ndy*nrz + ndz*nry);
        m.m31 = 2f * (-ndw*nry + ndx*nrz + ndy*nrw - ndz*nrx);
        m.m32 = 2f * (-ndw*nrz - ndx*nry + ndy*nrx + ndz*nrw);
        m.m33 = 1f;
        return m;
    }

    /**
     * Extracts the translation component.
     */
    public Vec3 extractTranslation() {
        Vec3 t = new Vec3();
        extractTranslation(t);
        return t;
    }

    /**
     * Extracts the translation component into the given vector.
     */
    public void extractTranslation(Vec3 out) {
        // t = 2 * dual * conj(real)
        out.x = 2f * (-dw*rx + dx*rw - dy*rz + dz*ry);
        out.y = 2f * (-dw*ry + dx*rz + dy*rw - dz*rx);
        out.z = 2f * (-dw*rz - dx*ry + dy*rx + dz*rw);
    }

    /**
     * Extracts the rotation component as a quaternion.
     */
    public Quaternion extractRotation() {
        return new Quaternion(rx, ry, rz, rw);
    }

    /**
     * Extracts the rotation component into the given quaternion.
     */
    public void extractRotation(Quaternion out) {
        out.x = rx; out.y = ry; out.z = rz; out.w = rw;
    }

    /**
     * Transforms a point by this dual quaternion.
     */
    public Vec3 transformPoint(Vec3 point) {
        Vec3 result = new Vec3(point);
        // Rotate by real part
        Quaternion r = new Quaternion(rx, ry, rz, rw);
        r.rotateVector(result);
        // Add translation
        Vec3 t = extractTranslation();
        result.add(t);
        return result;
    }

    // --- Queries ---

    public float realLengthSquared() {
        return rx*rx + ry*ry + rz*rz + rw*rw;
    }

    public float realLength() {
        return (float) Math.sqrt(realLengthSquared());
    }

    // --- Object overrides ---

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof DualQuaternion other)) return false;
        return Float.compare(rx, other.rx) == 0 && Float.compare(ry, other.ry) == 0
            && Float.compare(rz, other.rz) == 0 && Float.compare(rw, other.rw) == 0
            && Float.compare(dx, other.dx) == 0 && Float.compare(dy, other.dy) == 0
            && Float.compare(dz, other.dz) == 0 && Float.compare(dw, other.dw) == 0;
    }

    @Override
    public int hashCode() {
        int result = Float.hashCode(rx);
        result = 31 * result + Float.hashCode(ry);
        result = 31 * result + Float.hashCode(rz);
        result = 31 * result + Float.hashCode(rw);
        result = 31 * result + Float.hashCode(dx);
        result = 31 * result + Float.hashCode(dy);
        result = 31 * result + Float.hashCode(dz);
        result = 31 * result + Float.hashCode(dw);
        return result;
    }

    @Override
    public String toString() {
        return "DualQuaternion(real=(" + rx + ", " + ry + ", " + rz + ", " + rw
             + "), dual=(" + dx + ", " + dy + ", " + dz + ", " + dw + "))";
    }

    // --- BufferWritable ---

    @Override
    public int byteSize() { return REAL_DUAL.byteSize(); }

    @Override
    public void writeTo(ByteBuffer buf) { REAL_DUAL.writeTo(this, buf); }

    @Override
    public void readFrom(ByteBuffer buf) { REAL_DUAL.readFrom(this, buf); }

    public void writeTo(ByteBuffer buf, GpuLayout<DualQuaternion> layout) { layout.writeTo(this, buf); }

    public void readFrom(ByteBuffer buf, GpuLayout<DualQuaternion> layout) { layout.readFrom(this, buf); }

    // --- BuildStrategy ---

    public static void setBuildStrategy(BuildStrategy<DualQuaternion> strategy) {
        buildStrategy = strategy;
    }

    public static BuildStrategy<DualQuaternion> getBuildStrategy() {
        return buildStrategy;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final boolean eager;
        private Quaternion rotation;
        private Vec3 translation;

        private enum Mode { IDENTITY, ROTATION_TRANSLATION, ROTATION, TRANSLATION }
        private Mode mode = Mode.IDENTITY;

        public Builder() { this.eager = false; }
        private Builder(boolean eager) { this.eager = eager; }

        /** Returns a builder that will eagerly cache intermediate results (no-op currently). */
        public static Builder eager() { return new Builder(true); }
        /** Returns a builder that defers all computation to build() time (default). */
        public static Builder lazy() { return new Builder(false); }

        public Builder fromRotationTranslation(Quaternion rotation, Vec3 translation) {
            this.rotation = rotation; this.translation = translation;
            this.mode = Mode.ROTATION_TRANSLATION;
            return this;
        }

        public Builder fromRotation(Quaternion rotation) {
            this.rotation = rotation;
            this.mode = Mode.ROTATION;
            return this;
        }

        public Builder fromTranslation(Vec3 translation) {
            this.translation = translation;
            this.mode = Mode.TRANSLATION;
            return this;
        }

        public Builder identity() {
            this.mode = Mode.IDENTITY;
            return this;
        }

        public DualQuaternion build() {
            return apply(buildStrategy.obtain());
        }

        public DualQuaternion build(BuildStrategy<DualQuaternion> strategy) {
            return apply(strategy.obtain());
        }

        // NOTE: apply() could be invoked eagerly to cache intermediate results when parameters change,
        // but is not yet implemented that way. Currently all computation happens at build() time.
        private DualQuaternion apply(DualQuaternion dq) {
            switch (mode) {
                case IDENTITY -> dq.setIdentity();
                case ROTATION_TRANSLATION -> dq.set(rotation, translation);
                case ROTATION -> {
                    dq.rx = rotation.x; dq.ry = rotation.y; dq.rz = rotation.z; dq.rw = rotation.w;
                    dq.dx = 0f; dq.dy = 0f; dq.dz = 0f; dq.dw = 0f;
                }
                case TRANSLATION -> {
                    dq.rx = 0f; dq.ry = 0f; dq.rz = 0f; dq.rw = 1f;
                    dq.dx = 0.5f * translation.x;
                    dq.dy = 0.5f * translation.y;
                    dq.dz = 0.5f * translation.z;
                    dq.dw = 0f;
                }
            }
            return dq;
        }
    }
}
