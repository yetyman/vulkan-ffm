package io.github.yetyman.helpers.math;

import io.github.yetyman.vulkan.buffers.GpuLayout;
import io.github.yetyman.vulkan.buffers.HasGpuLayout;

import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT_UNALIGNED;

/**
 * Mutable 4-component float vector.
 */
public class Vec4 implements HasGpuLayout<Vec4> {

    // --- GPU Layouts ---

    /** Default layout: x, y, z, w (16 bytes). */
    public static final GpuLayout<Vec4> XYZW = new GpuLayout<>() {
        @Override public int byteSize() { return 16; }
        @Override public void writeTo(Vec4 v, MemorySegment dst, long o) {
            dst.set(JAVA_FLOAT_UNALIGNED, o, v.x);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 4, v.y);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 8, v.z);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 12, v.w);
        }
        @Override public void readFrom(Vec4 v, MemorySegment src, long o) {
            v.x = src.get(JAVA_FLOAT_UNALIGNED, o);
            v.y = src.get(JAVA_FLOAT_UNALIGNED, o + 4);
            v.z = src.get(JAVA_FLOAT_UNALIGNED, o + 8);
            v.w = src.get(JAVA_FLOAT_UNALIGNED, o + 12);
        }
    };

    /** Canonical layout for this type. */
    public static final GpuLayout<Vec4> DEFAULT_LAYOUT = XYZW;

    /** RGBA color layout (same bytes, semantic alias). */
    public static final GpuLayout<Vec4> RGBA = XYZW;

    private static BuildStrategy<Vec4> buildStrategy = BuildStrategy.allocating(Vec4::new);

    public float x;
    public float y;
    public float z;
    public float w;

    public Vec4() {
        this.x = 0f;
        this.y = 0f;
        this.z = 0f;
        this.w = 0f;
    }

    public Vec4(float x, float y, float z, float w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
    }

    public Vec4(Vec3 xyz, float w) {
        this.x = xyz.x;
        this.y = xyz.y;
        this.z = xyz.z;
        this.w = w;
    }

    public Vec4(Vec4 other) {
        this.x = other.x;
        this.y = other.y;
        this.z = other.z;
        this.w = other.w;
    }

    public Vec4(float scalar) {
        this.x = scalar;
        this.y = scalar;
        this.z = scalar;
        this.w = scalar;
    }

    // --- Static factories ---

    public static Vec4 zero() { return new Vec4(0f, 0f, 0f, 0f); }
    public static Vec4 one() { return new Vec4(1f, 1f, 1f, 1f); }
    public static Vec4 unitX() { return new Vec4(1f, 0f, 0f, 0f); }
    public static Vec4 unitY() { return new Vec4(0f, 1f, 0f, 0f); }
    public static Vec4 unitZ() { return new Vec4(0f, 0f, 1f, 0f); }
    public static Vec4 unitW() { return new Vec4(0f, 0f, 0f, 1f); }

    // --- Setters ---

    public Vec4 set(float x, float y, float z, float w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
        return this;
    }

    public Vec4 set(Vec4 other) {
        this.x = other.x;
        this.y = other.y;
        this.z = other.z;
        this.w = other.w;
        return this;
    }

    public Vec4 set(Vec3 xyz, float w) {
        this.x = xyz.x;
        this.y = xyz.y;
        this.z = xyz.z;
        this.w = w;
        return this;
    }

    public Vec4 set(float scalar) {
        this.x = scalar;
        this.y = scalar;
        this.z = scalar;
        this.w = scalar;
        return this;
    }

    // --- In-place operations ---

    public Vec4 add(Vec4 other) {
        this.x += other.x;
        this.y += other.y;
        this.z += other.z;
        this.w += other.w;
        return this;
    }

    public Vec4 add(float x, float y, float z, float w) {
        this.x += x;
        this.y += y;
        this.z += z;
        this.w += w;
        return this;
    }

    public Vec4 sub(Vec4 other) {
        this.x -= other.x;
        this.y -= other.y;
        this.z -= other.z;
        this.w -= other.w;
        return this;
    }

    public Vec4 sub(float x, float y, float z, float w) {
        this.x -= x;
        this.y -= y;
        this.z -= z;
        this.w -= w;
        return this;
    }

    public Vec4 mul(Vec4 other) {
        this.x *= other.x;
        this.y *= other.y;
        this.z *= other.z;
        this.w *= other.w;
        return this;
    }

    public Vec4 mul(float scalar) {
        this.x *= scalar;
        this.y *= scalar;
        this.z *= scalar;
        this.w *= scalar;
        return this;
    }

    public Vec4 div(Vec4 other) {
        this.x /= other.x;
        this.y /= other.y;
        this.z /= other.z;
        this.w /= other.w;
        return this;
    }

    public Vec4 div(float scalar) {
        float inv = 1f / scalar;
        this.x *= inv;
        this.y *= inv;
        this.z *= inv;
        this.w *= inv;
        return this;
    }

    public Vec4 negate() {
        this.x = -this.x;
        this.y = -this.y;
        this.z = -this.z;
        this.w = -this.w;
        return this;
    }

    public Vec4 normalize() {
        float len = length();
        if (len < MathUtil.EPSILON) {
            this.x = 0f;
            this.y = 0f;
            this.z = 0f;
            this.w = 0f;
            return this;
        }
        float inv = 1f / len;
        this.x *= inv;
        this.y *= inv;
        this.z *= inv;
        this.w *= inv;
        return this;
    }

    public Vec4 lerp(Vec4 other, float t) {
        this.x = MathUtil.lerp(this.x, other.x, t);
        this.y = MathUtil.lerp(this.y, other.y, t);
        this.z = MathUtil.lerp(this.z, other.z, t);
        this.w = MathUtil.lerp(this.w, other.w, t);
        return this;
    }

    public Vec4 min(Vec4 other) {
        this.x = Math.min(this.x, other.x);
        this.y = Math.min(this.y, other.y);
        this.z = Math.min(this.z, other.z);
        this.w = Math.min(this.w, other.w);
        return this;
    }

    public Vec4 max(Vec4 other) {
        this.x = Math.max(this.x, other.x);
        this.y = Math.max(this.y, other.y);
        this.z = Math.max(this.z, other.z);
        this.w = Math.max(this.w, other.w);
        return this;
    }

    public Vec4 clamp(Vec4 min, Vec4 max) {
        this.x = MathUtil.clamp(this.x, min.x, max.x);
        this.y = MathUtil.clamp(this.y, min.y, max.y);
        this.z = MathUtil.clamp(this.z, min.z, max.z);
        this.w = MathUtil.clamp(this.w, min.w, max.w);
        return this;
    }

    public Vec4 clamp(float min, float max) {
        this.x = MathUtil.clamp(this.x, min, max);
        this.y = MathUtil.clamp(this.y, min, max);
        this.z = MathUtil.clamp(this.z, min, max);
        this.w = MathUtil.clamp(this.w, min, max);
        return this;
    }

    public Vec4 abs() {
        this.x = Math.abs(this.x);
        this.y = Math.abs(this.y);
        this.z = Math.abs(this.z);
        this.w = Math.abs(this.w);
        return this;
    }

    public Vec4 floor() {
        this.x = (float) Math.floor(this.x);
        this.y = (float) Math.floor(this.y);
        this.z = (float) Math.floor(this.z);
        this.w = (float) Math.floor(this.w);
        return this;
    }

    public Vec4 ceil() {
        this.x = (float) Math.ceil(this.x);
        this.y = (float) Math.ceil(this.y);
        this.z = (float) Math.ceil(this.z);
        this.w = (float) Math.ceil(this.w);
        return this;
    }

    /**
     * Divides xyz by w (perspective divide). Sets w to 1.
     */
    public Vec4 perspectiveDivide() {
        if (Math.abs(w) < MathUtil.EPSILON) {
            return this;
        }
        float inv = 1f / w;
        this.x *= inv;
        this.y *= inv;
        this.z *= inv;
        this.w = 1f;
        return this;
    }

    // --- Copy operations ---

    public Vec4 addNew(Vec4 other) {
        return new Vec4(this.x + other.x, this.y + other.y, this.z + other.z, this.w + other.w);
    }

    public Vec4 subNew(Vec4 other) {
        return new Vec4(this.x - other.x, this.y - other.y, this.z - other.z, this.w - other.w);
    }

    public Vec4 mulNew(Vec4 other) {
        return new Vec4(this.x * other.x, this.y * other.y, this.z * other.z, this.w * other.w);
    }

    public Vec4 mulNew(float scalar) {
        return new Vec4(this.x * scalar, this.y * scalar, this.z * scalar, this.w * scalar);
    }

    public Vec4 divNew(Vec4 other) {
        return new Vec4(this.x / other.x, this.y / other.y, this.z / other.z, this.w / other.w);
    }

    public Vec4 divNew(float scalar) {
        float inv = 1f / scalar;
        return new Vec4(this.x * inv, this.y * inv, this.z * inv, this.w * inv);
    }

    public Vec4 negateNew() {
        return new Vec4(-this.x, -this.y, -this.z, -this.w);
    }

    public Vec4 normalizeNew() {
        float len = length();
        if (len < MathUtil.EPSILON) {
            return new Vec4(0f, 0f, 0f, 0f);
        }
        float inv = 1f / len;
        return new Vec4(this.x * inv, this.y * inv, this.z * inv, this.w * inv);
    }

    public Vec4 lerpNew(Vec4 other, float t) {
        return new Vec4(
                MathUtil.lerp(this.x, other.x, t),
                MathUtil.lerp(this.y, other.y, t),
                MathUtil.lerp(this.z, other.z, t),
                MathUtil.lerp(this.w, other.w, t)
        );
    }

    // --- Accessors ---

    /**
     * Returns a new Vec3 containing the xyz components.
     */
    public Vec3 xyz() {
        return new Vec3(x, y, z);
    }

    /**
     * Sets the xyz components from the given Vec3.
     */
    public Vec4 setXyz(Vec3 xyz) {
        this.x = xyz.x;
        this.y = xyz.y;
        this.z = xyz.z;
        return this;
    }

    // --- Queries ---

    public float dot(Vec4 other) {
        return this.x * other.x + this.y * other.y + this.z * other.z + this.w * other.w;
    }

    public float length() {
        return (float) Math.sqrt(x * x + y * y + z * z + w * w);
    }

    public float lengthSquared() {
        return x * x + y * y + z * z + w * w;
    }

    public float distance(Vec4 other) {
        float dx = this.x - other.x;
        float dy = this.y - other.y;
        float dz = this.z - other.z;
        float dw = this.w - other.w;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz + dw * dw);
    }

    public float distanceSquared(Vec4 other) {
        float dx = this.x - other.x;
        float dy = this.y - other.y;
        float dz = this.z - other.z;
        float dw = this.w - other.w;
        return dx * dx + dy * dy + dz * dz + dw * dw;
    }

    // --- Array interop ---

    public float[] toArray() {
        return new float[]{x, y, z, w};
    }

    public float[] toArray(float[] out, int offset) {
        out[offset] = x;
        out[offset + 1] = y;
        out[offset + 2] = z;
        out[offset + 3] = w;
        return out;
    }

    public Vec4 fromArray(float[] arr, int offset) {
        this.x = arr[offset];
        this.y = arr[offset + 1];
        this.z = arr[offset + 2];
        this.w = arr[offset + 3];
        return this;
    }

    // --- Object overrides ---

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Vec4 other)) return false;
        return Float.compare(x, other.x) == 0
                && Float.compare(y, other.y) == 0
                && Float.compare(z, other.z) == 0
                && Float.compare(w, other.w) == 0;
    }

    public boolean epsilonEquals(Vec4 other) {
        return MathUtil.epsilonEquals(x, other.x)
                && MathUtil.epsilonEquals(y, other.y)
                && MathUtil.epsilonEquals(z, other.z)
                && MathUtil.epsilonEquals(w, other.w);
    }

    public boolean epsilonEquals(Vec4 other, float tolerance) {
        return MathUtil.epsilonEquals(x, other.x, tolerance)
                && MathUtil.epsilonEquals(y, other.y, tolerance)
                && MathUtil.epsilonEquals(z, other.z, tolerance)
                && MathUtil.epsilonEquals(w, other.w, tolerance);
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
        return "Vec4(" + x + ", " + y + ", " + z + ", " + w + ")";
    }

    // --- HasGpuLayout ---

    @Override
    public GpuLayout<Vec4> defaultLayout() { return DEFAULT_LAYOUT; }

    /** Writes this vector into {@code dst} at {@code offset} using the default layout. */
    public void writeTo(MemorySegment dst, long offset) { DEFAULT_LAYOUT.writeTo(this, dst, offset); }

    /** Reads this vector from {@code src} at {@code offset} using the default layout. */
    public void readFrom(MemorySegment src, long offset) { DEFAULT_LAYOUT.readFrom(this, src, offset); }

    /** Writes this vector into {@code dst} at {@code offset} using an alternative layout. */
    public void writeTo(MemorySegment dst, long offset, GpuLayout<Vec4> layout) { layout.writeTo(this, dst, offset); }

    /** Reads this vector from {@code src} at {@code offset} using an alternative layout. */
    public void readFrom(MemorySegment src, long offset, GpuLayout<Vec4> layout) { layout.readFrom(this, src, offset); }

    // --- BuildStrategy ---

    public static void setBuildStrategy(BuildStrategy<Vec4> strategy) {
        buildStrategy = strategy;
    }

    public static BuildStrategy<Vec4> getBuildStrategy() {
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
        private float w;
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
        public Builder w(float w) { this.w = w; return this; }
        public Builder xyz(Vec3 xyz) { this.x = xyz.x; this.y = xyz.y; this.z = xyz.z; return this; }

        public Vec4 build() {
            Vec4 v = buildStrategy.obtain();
            v.x = this.x;
            v.y = this.y;
            v.z = this.z;
            v.w = this.w;
            return v;
        }

        public Vec4 build(BuildStrategy<Vec4> strategy) {
            Vec4 v = strategy.obtain();
            v.x = this.x;
            v.y = this.y;
            v.z = this.z;
            v.w = this.w;
            return v;
        }
    }
}
