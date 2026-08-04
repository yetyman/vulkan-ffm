package io.github.yetyman.helpers.math;

import io.github.yetyman.vulkan.buffers.BufferWritable;
import io.github.yetyman.vulkan.buffers.GpuLayout;

import java.nio.ByteBuffer;

/**
 * Mutable unit quaternion for rotations.
 * Convention: identity = (0, 0, 0, 1).
 */
public class Quaternion implements BufferWritable {

    // --- GPU Layouts ---

    /** Default layout: x, y, z, w (16 bytes). */
    public static final GpuLayout<Quaternion> XYZW = new GpuLayout<>() {
        @Override public int byteSize() { return 16; }
        @Override public void writeTo(Quaternion q, ByteBuffer buf) { buf.putFloat(q.x); buf.putFloat(q.y); buf.putFloat(q.z); buf.putFloat(q.w); }
        @Override public void readFrom(Quaternion q, ByteBuffer buf) { q.x = buf.getFloat(); q.y = buf.getFloat(); q.z = buf.getFloat(); q.w = buf.getFloat(); }
    };

    /** Default layout used by BufferWritable methods. */
    public static final GpuLayout<Quaternion> DEFAULT_LAYOUT = XYZW;

    /** W-first layout: w, x, y, z (16 bytes). Some engines/formats use this convention. */
    public static final GpuLayout<Quaternion> WXYZ = new GpuLayout<>() {
        @Override public int byteSize() { return 16; }
        @Override public void writeTo(Quaternion q, ByteBuffer buf) { buf.putFloat(q.w); buf.putFloat(q.x); buf.putFloat(q.y); buf.putFloat(q.z); }
        @Override public void readFrom(Quaternion q, ByteBuffer buf) { q.w = buf.getFloat(); q.x = buf.getFloat(); q.y = buf.getFloat(); q.z = buf.getFloat(); }
    };

    private static BuildStrategy<Quaternion> buildStrategy = BuildStrategy.allocating(Quaternion::new);

    public float x;
    public float y;
    public float z;
    public float w;

    /** Creates an identity quaternion. */
    public Quaternion() {
        this.x = 0f;
        this.y = 0f;
        this.z = 0f;
        this.w = 1f;
    }

    public Quaternion(float x, float y, float z, float w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
    }

    public Quaternion(Quaternion other) {
        this.x = other.x;
        this.y = other.y;
        this.z = other.z;
        this.w = other.w;
    }

    // --- Static factories ---

    public static Quaternion identity() {
        return new Quaternion(0f, 0f, 0f, 1f);
    }

    public static Quaternion fromAxisAngle(Vec3 axis, float angle) {
        float len = axis.length();
        if (len < MathUtil.EPSILON) return identity();
        float inv = 1f / len;
        float halfAngle = angle * 0.5f;
        float s = (float) Math.sin(halfAngle);
        return new Quaternion(
                axis.x * inv * s,
                axis.y * inv * s,
                axis.z * inv * s,
                (float) Math.cos(halfAngle)
        );
    }

    /**
     * Creates a quaternion from Euler angles (in radians).
     * Order: YXZ (yaw, pitch, roll) — common for FPS-style cameras.
     */
    public static Quaternion fromEuler(float pitch, float yaw, float roll) {
        float cy = (float) Math.cos(yaw * 0.5f);
        float sy = (float) Math.sin(yaw * 0.5f);
        float cp = (float) Math.cos(pitch * 0.5f);
        float sp = (float) Math.sin(pitch * 0.5f);
        float cr = (float) Math.cos(roll * 0.5f);
        float sr = (float) Math.sin(roll * 0.5f);

        return new Quaternion(
                cy * sp * cr + sy * cp * sr,
                sy * cp * cr - cy * sp * sr,
                cy * cp * sr - sy * sp * cr,
                cy * cp * cr + sy * sp * sr
        );
    }

    public static Quaternion fromMat3(Mat3 m) {
        float trace = m.m00 + m.m11 + m.m22;
        Quaternion q = new Quaternion();
        if (trace > 0f) {
            float s = (float) Math.sqrt(trace + 1f) * 2f;
            q.w = 0.25f * s;
            q.x = (m.m12 - m.m21) / s;
            q.y = (m.m20 - m.m02) / s;
            q.z = (m.m01 - m.m10) / s;
        } else if (m.m00 > m.m11 && m.m00 > m.m22) {
            float s = (float) Math.sqrt(1f + m.m00 - m.m11 - m.m22) * 2f;
            q.w = (m.m12 - m.m21) / s;
            q.x = 0.25f * s;
            q.y = (m.m10 + m.m01) / s;
            q.z = (m.m20 + m.m02) / s;
        } else if (m.m11 > m.m22) {
            float s = (float) Math.sqrt(1f + m.m11 - m.m00 - m.m22) * 2f;
            q.w = (m.m20 - m.m02) / s;
            q.x = (m.m10 + m.m01) / s;
            q.y = 0.25f * s;
            q.z = (m.m21 + m.m12) / s;
        } else {
            float s = (float) Math.sqrt(1f + m.m22 - m.m00 - m.m11) * 2f;
            q.w = (m.m01 - m.m10) / s;
            q.x = (m.m20 + m.m02) / s;
            q.y = (m.m21 + m.m12) / s;
            q.z = 0.25f * s;
        }
        return q;
    }

    public static Quaternion fromMat4(Mat4 m) {
        Mat3 rot = new Mat3();
        rot.m00 = m.m00; rot.m01 = m.m01; rot.m02 = m.m02;
        rot.m10 = m.m10; rot.m11 = m.m11; rot.m12 = m.m12;
        rot.m20 = m.m20; rot.m21 = m.m21; rot.m22 = m.m22;
        return fromMat3(rot);
    }

    /**
     * Creates a look rotation quaternion from a forward direction and up hint.
     */
    public static Quaternion fromDirection(Vec3 forward, Vec3 up) {
        Vec3 f = forward.normalizeNew();
        Vec3 r = up.crossNew(f).normalize();
        Vec3 u = f.crossNew(r);
        Mat3 m = new Mat3(r, u, f);
        return fromMat3(m);
    }

    // --- Setters ---

    public Quaternion set(float x, float y, float z, float w) {
        this.x = x; this.y = y; this.z = z; this.w = w;
        return this;
    }

    public Quaternion set(Quaternion other) {
        this.x = other.x; this.y = other.y; this.z = other.z; this.w = other.w;
        return this;
    }

    // --- In-place operations ---

    /**
     * Multiplies this quaternion by other: this = this * other.
     */
    public Quaternion multiply(Quaternion other) {
        float nx = w*other.x + x*other.w + y*other.z - z*other.y;
        float ny = w*other.y - x*other.z + y*other.w + z*other.x;
        float nz = w*other.z + x*other.y - y*other.x + z*other.w;
        float nw = w*other.w - x*other.x - y*other.y - z*other.z;
        this.x = nx; this.y = ny; this.z = nz; this.w = nw;
        return this;
    }

    public Quaternion conjugate() {
        this.x = -this.x;
        this.y = -this.y;
        this.z = -this.z;
        return this;
    }

    public Quaternion inverse() {
        float lenSq = lengthSquared();
        if (lenSq < MathUtil.EPSILON) return this;
        float inv = 1f / lenSq;
        this.x = -this.x * inv;
        this.y = -this.y * inv;
        this.z = -this.z * inv;
        this.w = this.w * inv;
        return this;
    }

    public Quaternion normalize() {
        float len = length();
        if (len < MathUtil.EPSILON) {
            this.x = 0f; this.y = 0f; this.z = 0f; this.w = 1f;
            return this;
        }
        float inv = 1f / len;
        this.x *= inv; this.y *= inv; this.z *= inv; this.w *= inv;
        return this;
    }

    // --- Copy operations ---

    public Quaternion multiplyNew(Quaternion other) {
        return new Quaternion(
                w*other.x + x*other.w + y*other.z - z*other.y,
                w*other.y - x*other.z + y*other.w + z*other.x,
                w*other.z + x*other.y - y*other.x + z*other.w,
                w*other.w - x*other.x - y*other.y - z*other.z
        );
    }

    public Quaternion conjugateNew() {
        return new Quaternion(-x, -y, -z, w);
    }

    public Quaternion inverseNew() {
        return new Quaternion(this).inverse();
    }

    public Quaternion normalizeNew() {
        return new Quaternion(this).normalize();
    }

    // --- Interpolation ---

    /**
     * Spherical linear interpolation in-place.
     */
    public Quaternion slerp(Quaternion other, float t) {
        float dot = dot(other);
        float ox = other.x, oy = other.y, oz = other.z, ow = other.w;
        if (dot < 0f) {
            dot = -dot;
            ox = -ox; oy = -oy; oz = -oz; ow = -ow;
        }
        float s0, s1;
        if (dot > 0.9995f) {
            // Linear fallback for nearly identical orientations
            s0 = 1f - t;
            s1 = t;
        } else {
            float angle = (float) Math.acos(dot);
            float sinAngle = (float) Math.sin(angle);
            s0 = (float) Math.sin((1f - t) * angle) / sinAngle;
            s1 = (float) Math.sin(t * angle) / sinAngle;
        }
        this.x = s0*x + s1*ox;
        this.y = s0*y + s1*oy;
        this.z = s0*z + s1*oz;
        this.w = s0*w + s1*ow;
        return this;
    }

    public Quaternion slerpNew(Quaternion other, float t) {
        return new Quaternion(this).slerp(other, t);
    }

    /**
     * Normalized linear interpolation in-place.
     */
    public Quaternion nlerp(Quaternion other, float t) {
        float dot = dot(other);
        float ox = other.x, oy = other.y, oz = other.z, ow = other.w;
        if (dot < 0f) {
            ox = -ox; oy = -oy; oz = -oz; ow = -ow;
        }
        this.x = MathUtil.lerp(x, ox, t);
        this.y = MathUtil.lerp(y, oy, t);
        this.z = MathUtil.lerp(z, oz, t);
        this.w = MathUtil.lerp(w, ow, t);
        normalize();
        return this;
    }

    public Quaternion nlerpNew(Quaternion other, float t) {
        return new Quaternion(this).nlerp(other, t);
    }

    // --- Conversion ---

    public Mat3 toMat3() {
        float xx = x*x, yy = y*y, zz = z*z;
        float xy = x*y, xz = x*z, yz = y*z;
        float wx = w*x, wy = w*y, wz = w*z;

        Mat3 m = new Mat3();
        m.m00 = 1f - 2f*(yy + zz); m.m01 = 2f*(xy + wz);       m.m02 = 2f*(xz - wy);
        m.m10 = 2f*(xy - wz);       m.m11 = 1f - 2f*(xx + zz); m.m12 = 2f*(yz + wx);
        m.m20 = 2f*(xz + wy);       m.m21 = 2f*(yz - wx);       m.m22 = 1f - 2f*(xx + yy);
        return m;
    }

    public Mat4 toMat4() {
        float xx = x*x, yy = y*y, zz = z*z;
        float xy = x*y, xz = x*z, yz = y*z;
        float wx = w*x, wy = w*y, wz = w*z;

        Mat4 m = new Mat4();
        m.m00 = 1f - 2f*(yy + zz); m.m01 = 2f*(xy + wz);       m.m02 = 2f*(xz - wy);       m.m03 = 0f;
        m.m10 = 2f*(xy - wz);       m.m11 = 1f - 2f*(xx + zz); m.m12 = 2f*(yz + wx);       m.m13 = 0f;
        m.m20 = 2f*(xz + wy);       m.m21 = 2f*(yz - wx);       m.m22 = 1f - 2f*(xx + yy); m.m23 = 0f;
        m.m30 = 0f;                  m.m31 = 0f;                  m.m32 = 0f;                  m.m33 = 1f;
        return m;
    }

    /**
     * Extracts axis-angle representation.
     * @param outAxis receives the rotation axis (normalized)
     * @return the rotation angle in radians
     */
    public float toAxisAngle(Vec3 outAxis) {
        float len = (float) Math.sqrt(x*x + y*y + z*z);
        if (len < MathUtil.EPSILON) {
            outAxis.set(0f, 1f, 0f);
            return 0f;
        }
        float inv = 1f / len;
        outAxis.set(x * inv, y * inv, z * inv);
        return 2f * (float) Math.atan2(len, w);
    }

    /**
     * Converts to Euler angles (pitch, yaw, roll) in radians.
     * @param outAngles receives (pitch=x, yaw=y, roll=z)
     */
    public void toEuler(Vec3 outAngles) {
        // Pitch (X)
        float sinP = 2f * (w*x + y*z);
        float cosP = 1f - 2f*(x*x + y*y);
        outAngles.x = (float) Math.atan2(sinP, cosP);
        // Yaw (Y)
        float sinY = 2f * (w*y - z*x);
        if (Math.abs(sinY) >= 1f) {
            outAngles.y = (float) Math.copySign(MathUtil.HALF_PI, sinY);
        } else {
            outAngles.y = (float) Math.asin(sinY);
        }
        // Roll (Z)
        float sinR = 2f * (w*z + x*y);
        float cosR = 1f - 2f*(y*y + z*z);
        outAngles.z = (float) Math.atan2(sinR, cosR);
    }

    // --- Apply ---

    /**
     * Rotates the given vector by this quaternion in-place: v = q * v * q^-1.
     */
    public Vec3 rotateVector(Vec3 v) {
        float tx = 2f * (y*v.z - z*v.y);
        float ty = 2f * (z*v.x - x*v.z);
        float tz = 2f * (x*v.y - y*v.x);
        v.x += w*tx + (y*tz - z*ty);
        v.y += w*ty + (z*tx - x*tz);
        v.z += w*tz + (x*ty - y*tx);
        return v;
    }

    // --- Queries ---

    public float dot(Quaternion other) {
        return x*other.x + y*other.y + z*other.z + w*other.w;
    }

    public float length() {
        return (float) Math.sqrt(x*x + y*y + z*z + w*w);
    }

    public float lengthSquared() {
        return x*x + y*y + z*z + w*w;
    }

    // --- Object overrides ---

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Quaternion other)) return false;
        return Float.compare(x, other.x) == 0 && Float.compare(y, other.y) == 0
                && Float.compare(z, other.z) == 0 && Float.compare(w, other.w) == 0;
    }

    @Override
    public int hashCode() {
        int result = Float.hashCode(x);
        result = 31 * result + Float.hashCode(y);
        result = 31 * result + Float.hashCode(z);
        result = 31 * result + Float.hashCode(w);
        return result;
    }

    @Override
    public String toString() {
        return "Quaternion(" + x + ", " + y + ", " + z + ", " + w + ")";
    }

    // --- BufferWritable ---

    @Override
    public int byteSize() { return XYZW.byteSize(); }

    @Override
    public void writeTo(ByteBuffer buf) { XYZW.writeTo(this, buf); }

    @Override
    public void readFrom(ByteBuffer buf) { XYZW.readFrom(this, buf); }

    public void writeTo(ByteBuffer buf, GpuLayout<Quaternion> layout) { layout.writeTo(this, buf); }

    public void readFrom(ByteBuffer buf, GpuLayout<Quaternion> layout) { layout.readFrom(this, buf); }

    // --- BuildStrategy ---

    public static void setBuildStrategy(BuildStrategy<Quaternion> strategy) {
        buildStrategy = strategy;
    }

    public static BuildStrategy<Quaternion> getBuildStrategy() {
        return buildStrategy;
    }

    // --- Builder ---

    public static QuaternionBuilder builder() {
        return new QuaternionBuilder();
    }

    /**
     * Builder with memoization for axis-angle construction.
     * Caches normalized axis and trig results independently.
     * Also supports other construction modes without caching.
     */
    public static class QuaternionBuilder {
        private final boolean eager;

        private float ax, ay, az; // normalized axis
        private float angle;
        private float cachedSinHalf = Float.NaN, cachedCosHalf = Float.NaN;
        private float lastAngle = Float.NaN;
        private float rawAx = Float.NaN, rawAy = Float.NaN, rawAz = Float.NaN;
        private boolean axisDirty = true;

        private enum Mode { AXIS_ANGLE, EULER, MAT3, MAT4, DIRECTION, IDENTITY }
        private Mode mode = Mode.IDENTITY;

        // Euler params
        private float pitch, yaw, roll;
        // Mat3/Mat4 params
        private Mat3 mat3Src;
        private Mat4 mat4Src;
        // Direction params
        private Vec3 forward, up;

        public QuaternionBuilder() { this.eager = false; }
        private QuaternionBuilder(boolean eager) { this.eager = eager; }

        /** Returns a builder that will eagerly cache intermediate results (no-op currently). */
        public static QuaternionBuilder eager() { return new QuaternionBuilder(true); }
        /** Returns a builder that defers all computation to build() time (default). */
        public static QuaternionBuilder lazy() { return new QuaternionBuilder(false); }

        public QuaternionBuilder fromAxisAngle(Vec3 axis, float angle) {
            if (axis.x != rawAx || axis.y != rawAy || axis.z != rawAz) {
                rawAx = axis.x; rawAy = axis.y; rawAz = axis.z;
                axisDirty = true;
            }
            this.angle = angle;
            this.mode = Mode.AXIS_ANGLE;
            return this;
        }

        public QuaternionBuilder fromEuler(float pitch, float yaw, float roll) {
            this.pitch = pitch; this.yaw = yaw; this.roll = roll;
            this.mode = Mode.EULER;
            return this;
        }

        public QuaternionBuilder fromMat3(Mat3 m) {
            this.mat3Src = m;
            this.mode = Mode.MAT3;
            return this;
        }

        public QuaternionBuilder fromMat4(Mat4 m) {
            this.mat4Src = m;
            this.mode = Mode.MAT4;
            return this;
        }

        public QuaternionBuilder fromDirection(Vec3 forward, Vec3 up) {
            this.forward = forward; this.up = up;
            this.mode = Mode.DIRECTION;
            return this;
        }

        public QuaternionBuilder identity() {
            this.mode = Mode.IDENTITY;
            return this;
        }

        public Quaternion build() {
            return buildInto(buildStrategy.obtain());
        }

        public Quaternion build(BuildStrategy<Quaternion> strategy) {
            return buildInto(strategy.obtain());
        }

        // NOTE: buildInto() could be invoked eagerly to cache intermediate results when parameters change,
        // but is not yet implemented that way (except for axis-angle which already caches).
        // Currently most computation happens at build() time.
        private Quaternion buildInto(Quaternion q) {
            switch (mode) {
                case AXIS_ANGLE -> {
                    if (axisDirty) {
                        float len = (float) Math.sqrt(rawAx*rawAx + rawAy*rawAy + rawAz*rawAz);
                        if (len < MathUtil.EPSILON) {
                            ax = 0f; ay = 0f; az = 0f;
                        } else {
                            float inv = 1f / len;
                            ax = rawAx * inv; ay = rawAy * inv; az = rawAz * inv;
                        }
                        axisDirty = false;
                    }
                    if (angle != lastAngle) {
                        float half = angle * 0.5f;
                        cachedSinHalf = (float) Math.sin(half);
                        cachedCosHalf = (float) Math.cos(half);
                        lastAngle = angle;
                    }
                    q.x = ax * cachedSinHalf;
                    q.y = ay * cachedSinHalf;
                    q.z = az * cachedSinHalf;
                    q.w = cachedCosHalf;
                }
                case EULER -> {
                    Quaternion tmp = Quaternion.fromEuler(pitch, yaw, roll);
                    q.set(tmp);
                }
                case MAT3 -> {
                    Quaternion tmp = Quaternion.fromMat3(mat3Src);
                    q.set(tmp);
                }
                case MAT4 -> {
                    Quaternion tmp = Quaternion.fromMat4(mat4Src);
                    q.set(tmp);
                }
                case DIRECTION -> {
                    Quaternion tmp = Quaternion.fromDirection(forward, up);
                    q.set(tmp);
                }
                case IDENTITY -> {
                    q.x = 0f; q.y = 0f; q.z = 0f; q.w = 1f;
                }
            }
            return q;
        }
    }
}
