package io.github.yetyman.helpers.math;

import io.github.yetyman.vulkan.buffers.BufferWritable;
import io.github.yetyman.vulkan.buffers.GpuLayout;

import java.nio.ByteBuffer;

/**
 * Mutable 3-component float vector.
 */
public class Vec3 implements BufferWritable {

    // --- GPU Layouts ---

    /** Default layout: x, y, z (12 bytes packed). */
    public static final GpuLayout<Vec3> XYZ = new GpuLayout<>() {
        @Override public int byteSize() { return 12; }
        @Override public void writeTo(Vec3 v, ByteBuffer buf) { buf.putFloat(v.x); buf.putFloat(v.y); buf.putFloat(v.z); }
        @Override public void readFrom(Vec3 v, ByteBuffer buf) { v.x = buf.getFloat(); v.y = buf.getFloat(); v.z = buf.getFloat(); }
    };

    /** Default layout used by BufferWritable methods. */
    public static final GpuLayout<Vec3> DEFAULT_LAYOUT = XYZ;

    /** std140-aligned layout: x, y, z, pad (16 bytes). */
    public static final GpuLayout<Vec3> STD140 = new GpuLayout<>() {
        @Override public int byteSize() { return 16; }
        @Override public void writeTo(Vec3 v, ByteBuffer buf) { buf.putFloat(v.x); buf.putFloat(v.y); buf.putFloat(v.z); buf.putFloat(0f); }
        @Override public void readFrom(Vec3 v, ByteBuffer buf) { v.x = buf.getFloat(); v.y = buf.getFloat(); v.z = buf.getFloat(); buf.getFloat(); }
    };

    private static BuildStrategy<Vec3> buildStrategy = BuildStrategy.allocating(Vec3::new);

    public float x;
    public float y;
    public float z;

    public Vec3() {
        this.x = 0f;
        this.y = 0f;
        this.z = 0f;
    }

    public Vec3(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3(Vec3 other) {
        this.x = other.x;
        this.y = other.y;
        this.z = other.z;
    }

    public Vec3(float scalar) {
        this.x = scalar;
        this.y = scalar;
        this.z = scalar;
    }

    // --- Static factories ---

    public static Vec3 zero() { return new Vec3(0f, 0f, 0f); }
    public static Vec3 one() { return new Vec3(1f, 1f, 1f); }
    public static Vec3 unitX() { return new Vec3(1f, 0f, 0f); }
    public static Vec3 unitY() { return new Vec3(0f, 1f, 0f); }
    public static Vec3 unitZ() { return new Vec3(0f, 0f, 1f); }
    public static Vec3 up() { return new Vec3(0f, 1f, 0f); }
    public static Vec3 down() { return new Vec3(0f, -1f, 0f); }
    public static Vec3 forward() { return new Vec3(0f, 0f, -1f); }
    public static Vec3 back() { return new Vec3(0f, 0f, 1f); }
    public static Vec3 left() { return new Vec3(-1f, 0f, 0f); }
    public static Vec3 right() { return new Vec3(1f, 0f, 0f); }

    // --- Setters ---

    public Vec3 set(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    public Vec3 set(Vec3 other) {
        this.x = other.x;
        this.y = other.y;
        this.z = other.z;
        return this;
    }

    public Vec3 set(float scalar) {
        this.x = scalar;
        this.y = scalar;
        this.z = scalar;
        return this;
    }

    // --- In-place operations ---

    public Vec3 add(Vec3 other) {
        this.x += other.x;
        this.y += other.y;
        this.z += other.z;
        return this;
    }

    public Vec3 add(float x, float y, float z) {
        this.x += x;
        this.y += y;
        this.z += z;
        return this;
    }

    public Vec3 sub(Vec3 other) {
        this.x -= other.x;
        this.y -= other.y;
        this.z -= other.z;
        return this;
    }

    public Vec3 sub(float x, float y, float z) {
        this.x -= x;
        this.y -= y;
        this.z -= z;
        return this;
    }

    public Vec3 mul(Vec3 other) {
        this.x *= other.x;
        this.y *= other.y;
        this.z *= other.z;
        return this;
    }

    public Vec3 mul(float scalar) {
        this.x *= scalar;
        this.y *= scalar;
        this.z *= scalar;
        return this;
    }

    public Vec3 div(Vec3 other) {
        this.x /= other.x;
        this.y /= other.y;
        this.z /= other.z;
        return this;
    }

    public Vec3 div(float scalar) {
        float inv = 1f / scalar;
        this.x *= inv;
        this.y *= inv;
        this.z *= inv;
        return this;
    }

    public Vec3 negate() {
        this.x = -this.x;
        this.y = -this.y;
        this.z = -this.z;
        return this;
    }

    public Vec3 normalize() {
        float len = length();
        if (len < MathUtil.EPSILON) {
            this.x = 0f;
            this.y = 0f;
            this.z = 0f;
            return this;
        }
        float inv = 1f / len;
        this.x *= inv;
        this.y *= inv;
        this.z *= inv;
        return this;
    }

    public Vec3 lerp(Vec3 other, float t) {
        this.x = MathUtil.lerp(this.x, other.x, t);
        this.y = MathUtil.lerp(this.y, other.y, t);
        this.z = MathUtil.lerp(this.z, other.z, t);
        return this;
    }

    public Vec3 min(Vec3 other) {
        this.x = Math.min(this.x, other.x);
        this.y = Math.min(this.y, other.y);
        this.z = Math.min(this.z, other.z);
        return this;
    }

    public Vec3 max(Vec3 other) {
        this.x = Math.max(this.x, other.x);
        this.y = Math.max(this.y, other.y);
        this.z = Math.max(this.z, other.z);
        return this;
    }

    public Vec3 clamp(Vec3 min, Vec3 max) {
        this.x = MathUtil.clamp(this.x, min.x, max.x);
        this.y = MathUtil.clamp(this.y, min.y, max.y);
        this.z = MathUtil.clamp(this.z, min.z, max.z);
        return this;
    }

    public Vec3 clamp(float min, float max) {
        this.x = MathUtil.clamp(this.x, min, max);
        this.y = MathUtil.clamp(this.y, min, max);
        this.z = MathUtil.clamp(this.z, min, max);
        return this;
    }

    public Vec3 abs() {
        this.x = Math.abs(this.x);
        this.y = Math.abs(this.y);
        this.z = Math.abs(this.z);
        return this;
    }

    public Vec3 floor() {
        this.x = (float) Math.floor(this.x);
        this.y = (float) Math.floor(this.y);
        this.z = (float) Math.floor(this.z);
        return this;
    }

    public Vec3 ceil() {
        this.x = (float) Math.ceil(this.x);
        this.y = (float) Math.ceil(this.y);
        this.z = (float) Math.ceil(this.z);
        return this;
    }

    /**
     * Sets this vector to the cross product of this and other: this = this x other.
     */
    public Vec3 cross(Vec3 other) {
        float cx = this.y * other.z - this.z * other.y;
        float cy = this.z * other.x - this.x * other.z;
        float cz = this.x * other.y - this.y * other.x;
        this.x = cx;
        this.y = cy;
        this.z = cz;
        return this;
    }

    /**
     * Reflects this vector about the given normal (assumed unit length).
     * Result: this = this - 2 * dot(this, normal) * normal
     */
    public Vec3 reflect(Vec3 normal) {
        float d = 2f * dot(normal);
        this.x -= d * normal.x;
        this.y -= d * normal.y;
        this.z -= d * normal.z;
        return this;
    }

    /**
     * Projects this vector onto the given vector (does not need to be normalized).
     * Result: this = (dot(this, onto) / dot(onto, onto)) * onto
     */
    public Vec3 project(Vec3 onto) {
        float d = onto.x * onto.x + onto.y * onto.y + onto.z * onto.z;
        if (d < MathUtil.EPSILON) {
            this.x = 0f;
            this.y = 0f;
            this.z = 0f;
            return this;
        }
        float scale = dot(onto) / d;
        this.x = onto.x * scale;
        this.y = onto.y * scale;
        this.z = onto.z * scale;
        return this;
    }

    // --- Copy operations ---

    public Vec3 addNew(Vec3 other) {
        return new Vec3(this.x + other.x, this.y + other.y, this.z + other.z);
    }

    public Vec3 subNew(Vec3 other) {
        return new Vec3(this.x - other.x, this.y - other.y, this.z - other.z);
    }

    public Vec3 mulNew(Vec3 other) {
        return new Vec3(this.x * other.x, this.y * other.y, this.z * other.z);
    }

    public Vec3 mulNew(float scalar) {
        return new Vec3(this.x * scalar, this.y * scalar, this.z * scalar);
    }

    public Vec3 divNew(Vec3 other) {
        return new Vec3(this.x / other.x, this.y / other.y, this.z / other.z);
    }

    public Vec3 divNew(float scalar) {
        float inv = 1f / scalar;
        return new Vec3(this.x * inv, this.y * inv, this.z * inv);
    }

    public Vec3 negateNew() {
        return new Vec3(-this.x, -this.y, -this.z);
    }

    public Vec3 normalizeNew() {
        float len = length();
        if (len < MathUtil.EPSILON) {
            return new Vec3(0f, 0f, 0f);
        }
        float inv = 1f / len;
        return new Vec3(this.x * inv, this.y * inv, this.z * inv);
    }

    public Vec3 lerpNew(Vec3 other, float t) {
        return new Vec3(
                MathUtil.lerp(this.x, other.x, t),
                MathUtil.lerp(this.y, other.y, t),
                MathUtil.lerp(this.z, other.z, t)
        );
    }

    public Vec3 crossNew(Vec3 other) {
        return new Vec3(
                this.y * other.z - this.z * other.y,
                this.z * other.x - this.x * other.z,
                this.x * other.y - this.y * other.x
        );
    }

    public Vec3 reflectNew(Vec3 normal) {
        float d = 2f * dot(normal);
        return new Vec3(this.x - d * normal.x, this.y - d * normal.y, this.z - d * normal.z);
    }

    public Vec3 projectNew(Vec3 onto) {
        float d = onto.x * onto.x + onto.y * onto.y + onto.z * onto.z;
        if (d < MathUtil.EPSILON) {
            return new Vec3(0f, 0f, 0f);
        }
        float scale = dot(onto) / d;
        return new Vec3(onto.x * scale, onto.y * scale, onto.z * scale);
    }

    // --- Queries ---

    public float dot(Vec3 other) {
        return this.x * other.x + this.y * other.y + this.z * other.z;
    }

    public float length() {
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    public float lengthSquared() {
        return x * x + y * y + z * z;
    }

    public float distance(Vec3 other) {
        float dx = this.x - other.x;
        float dy = this.y - other.y;
        float dz = this.z - other.z;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public float distanceSquared(Vec3 other) {
        float dx = this.x - other.x;
        float dy = this.y - other.y;
        float dz = this.z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Returns the angle in radians between this vector and other.
     */
    public float angleTo(Vec3 other) {
        float denom = length() * other.length();
        if (denom < MathUtil.EPSILON) return 0f;
        float cos = MathUtil.clamp(dot(other) / denom, -1f, 1f);
        return (float) Math.acos(cos);
    }

    // --- Array interop ---

    public float[] toArray() {
        return new float[]{x, y, z};
    }

    public float[] toArray(float[] out, int offset) {
        out[offset] = x;
        out[offset + 1] = y;
        out[offset + 2] = z;
        return out;
    }

    public Vec3 fromArray(float[] arr, int offset) {
        this.x = arr[offset];
        this.y = arr[offset + 1];
        this.z = arr[offset + 2];
        return this;
    }

    // --- Object overrides ---

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Vec3 other)) return false;
        return Float.compare(x, other.x) == 0
                && Float.compare(y, other.y) == 0
                && Float.compare(z, other.z) == 0;
    }

    public boolean epsilonEquals(Vec3 other) {
        return MathUtil.epsilonEquals(x, other.x)
                && MathUtil.epsilonEquals(y, other.y)
                && MathUtil.epsilonEquals(z, other.z);
    }

    public boolean epsilonEquals(Vec3 other, float tolerance) {
        return MathUtil.epsilonEquals(x, other.x, tolerance)
                && MathUtil.epsilonEquals(y, other.y, tolerance)
                && MathUtil.epsilonEquals(z, other.z, tolerance);
    }

    @Override
    public int hashCode() {
        int result = Float.hashCode(x);
        result = 31 * result + Float.hashCode(y);
        result = 31 * result + Float.hashCode(z);
        return result;
    }

    @Override
    public String toString() {
        return "Vec3(" + x + ", " + y + ", " + z + ")";
    }

    // --- BufferWritable ---

    @Override
    public int byteSize() { return XYZ.byteSize(); }

    @Override
    public void writeTo(ByteBuffer buf) { XYZ.writeTo(this, buf); }

    @Override
    public void readFrom(ByteBuffer buf) { XYZ.readFrom(this, buf); }

    public void writeTo(ByteBuffer buf, GpuLayout<Vec3> layout) { layout.writeTo(this, buf); }

    public void readFrom(ByteBuffer buf, GpuLayout<Vec3> layout) { layout.readFrom(this, buf); }

    // --- BuildStrategy ---

    public static void setBuildStrategy(BuildStrategy<Vec3> strategy) {
        buildStrategy = strategy;
    }

    public static BuildStrategy<Vec3> getBuildStrategy() {
        return buildStrategy;
    }

    // --- Builder ---

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private float x;
        private float y;
        private float z;
        private final boolean eager;

        public Builder() { this.eager = false; }
        private Builder(boolean eager) { this.eager = eager; }

        /** Returns a builder that will eagerly cache intermediate results (no-op currently). */
        public static Builder eager() { return new Builder(true); }
        /** Returns a builder that defers all computation to build() time (default). */
        public static Builder lazy() { return new Builder(false); }

        public Builder x(float x) { this.x = x; return this; }
        public Builder y(float y) { this.y = y; return this; }
        public Builder z(float z) { this.z = z; return this; }

        public Vec3 build() {
            Vec3 v = buildStrategy.obtain();
            v.x = this.x;
            v.y = this.y;
            v.z = this.z;
            return v;
        }

        public Vec3 build(BuildStrategy<Vec3> strategy) {
            Vec3 v = strategy.obtain();
            v.x = this.x;
            v.y = this.y;
            v.z = this.z;
            return v;
        }
    }
}
