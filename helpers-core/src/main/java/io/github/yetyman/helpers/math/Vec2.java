package io.github.yetyman.helpers.math;

import io.github.yetyman.vulkan.buffers.GpuLayout;
import io.github.yetyman.vulkan.buffers.HasGpuLayout;

import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT_UNALIGNED;

/**
 * Mutable 2-component float vector.
 */
public class Vec2 implements HasGpuLayout<Vec2> {

    // --- GPU Layouts ---

    /** Default layout: x, y (8 bytes). */
    public static final GpuLayout<Vec2> XY = new GpuLayout<>() {
        @Override public int byteSize() { return 8; }
        @Override public void writeTo(Vec2 v, MemorySegment dst, long o) {
            dst.set(JAVA_FLOAT_UNALIGNED, o, v.x);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 4, v.y);
        }
        @Override public void readFrom(Vec2 v, MemorySegment src, long o) {
            v.x = src.get(JAVA_FLOAT_UNALIGNED, o);
            v.y = src.get(JAVA_FLOAT_UNALIGNED, o + 4);
        }
    };

    /** Canonical layout for this type. */
    public static final GpuLayout<Vec2> DEFAULT_LAYOUT = XY;

    /** Reversed layout: y, x (8 bytes). */
    public static final GpuLayout<Vec2> YX = new GpuLayout<>() {
        @Override public int byteSize() { return 8; }
        @Override public void writeTo(Vec2 v, MemorySegment dst, long o) {
            dst.set(JAVA_FLOAT_UNALIGNED, o, v.y);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 4, v.x);
        }
        @Override public void readFrom(Vec2 v, MemorySegment src, long o) {
            v.y = src.get(JAVA_FLOAT_UNALIGNED, o);
            v.x = src.get(JAVA_FLOAT_UNALIGNED, o + 4);
        }
    };

    private static BuildStrategy<Vec2> buildStrategy = BuildStrategy.allocating(Vec2::new);

    public float x;
    public float y;

    public Vec2() {
        this.x = 0f;
        this.y = 0f;
    }

    public Vec2(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public Vec2(Vec2 other) {
        this.x = other.x;
        this.y = other.y;
    }

    public Vec2(float scalar) {
        this.x = scalar;
        this.y = scalar;
    }

    // --- Static factories ---

    public static Vec2 zero() { return new Vec2(0f, 0f); }
    public static Vec2 one() { return new Vec2(1f, 1f); }
    public static Vec2 unitX() { return new Vec2(1f, 0f); }
    public static Vec2 unitY() { return new Vec2(0f, 1f); }

    // --- Setters ---

    public Vec2 set(float x, float y) {
        this.x = x;
        this.y = y;
        return this;
    }

    public Vec2 set(Vec2 other) {
        this.x = other.x;
        this.y = other.y;
        return this;
    }

    public Vec2 set(float scalar) {
        this.x = scalar;
        this.y = scalar;
        return this;
    }

    // --- In-place operations ---

    public Vec2 add(Vec2 other) {
        this.x += other.x;
        this.y += other.y;
        return this;
    }

    public Vec2 add(float x, float y) {
        this.x += x;
        this.y += y;
        return this;
    }

    public Vec2 sub(Vec2 other) {
        this.x -= other.x;
        this.y -= other.y;
        return this;
    }

    public Vec2 sub(float x, float y) {
        this.x -= x;
        this.y -= y;
        return this;
    }

    public Vec2 mul(Vec2 other) {
        this.x *= other.x;
        this.y *= other.y;
        return this;
    }

    public Vec2 mul(float scalar) {
        this.x *= scalar;
        this.y *= scalar;
        return this;
    }

    public Vec2 div(Vec2 other) {
        this.x /= other.x;
        this.y /= other.y;
        return this;
    }

    public Vec2 div(float scalar) {
        float inv = 1f / scalar;
        this.x *= inv;
        this.y *= inv;
        return this;
    }

    public Vec2 negate() {
        this.x = -this.x;
        this.y = -this.y;
        return this;
    }

    public Vec2 normalize() {
        float len = length();
        if (len < MathUtil.EPSILON) {
            this.x = 0f;
            this.y = 0f;
            return this;
        }
        float inv = 1f / len;
        this.x *= inv;
        this.y *= inv;
        return this;
    }

    public Vec2 lerp(Vec2 other, float t) {
        this.x = MathUtil.lerp(this.x, other.x, t);
        this.y = MathUtil.lerp(this.y, other.y, t);
        return this;
    }

    public Vec2 min(Vec2 other) {
        this.x = Math.min(this.x, other.x);
        this.y = Math.min(this.y, other.y);
        return this;
    }

    public Vec2 max(Vec2 other) {
        this.x = Math.max(this.x, other.x);
        this.y = Math.max(this.y, other.y);
        return this;
    }

    public Vec2 clamp(Vec2 min, Vec2 max) {
        this.x = MathUtil.clamp(this.x, min.x, max.x);
        this.y = MathUtil.clamp(this.y, min.y, max.y);
        return this;
    }

    public Vec2 clamp(float min, float max) {
        this.x = MathUtil.clamp(this.x, min, max);
        this.y = MathUtil.clamp(this.y, min, max);
        return this;
    }

    public Vec2 abs() {
        this.x = Math.abs(this.x);
        this.y = Math.abs(this.y);
        return this;
    }

    public Vec2 floor() {
        this.x = (float) Math.floor(this.x);
        this.y = (float) Math.floor(this.y);
        return this;
    }

    public Vec2 ceil() {
        this.x = (float) Math.ceil(this.x);
        this.y = (float) Math.ceil(this.y);
        return this;
    }

    // --- Copy operations ---

    public Vec2 addNew(Vec2 other) {
        return new Vec2(this.x + other.x, this.y + other.y);
    }

    public Vec2 subNew(Vec2 other) {
        return new Vec2(this.x - other.x, this.y - other.y);
    }

    public Vec2 mulNew(Vec2 other) {
        return new Vec2(this.x * other.x, this.y * other.y);
    }

    public Vec2 mulNew(float scalar) {
        return new Vec2(this.x * scalar, this.y * scalar);
    }

    public Vec2 divNew(Vec2 other) {
        return new Vec2(this.x / other.x, this.y / other.y);
    }

    public Vec2 divNew(float scalar) {
        float inv = 1f / scalar;
        return new Vec2(this.x * inv, this.y * inv);
    }

    public Vec2 negateNew() {
        return new Vec2(-this.x, -this.y);
    }

    public Vec2 normalizeNew() {
        float len = length();
        if (len < MathUtil.EPSILON) {
            return new Vec2(0f, 0f);
        }
        float inv = 1f / len;
        return new Vec2(this.x * inv, this.y * inv);
    }

    public Vec2 lerpNew(Vec2 other, float t) {
        return new Vec2(MathUtil.lerp(this.x, other.x, t), MathUtil.lerp(this.y, other.y, t));
    }

    // --- Queries ---

    public float dot(Vec2 other) {
        return this.x * other.x + this.y * other.y;
    }

    public float length() {
        return (float) Math.sqrt(x * x + y * y);
    }

    public float lengthSquared() {
        return x * x + y * y;
    }

    public float distance(Vec2 other) {
        float dx = this.x - other.x;
        float dy = this.y - other.y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    public float distanceSquared(Vec2 other) {
        float dx = this.x - other.x;
        float dy = this.y - other.y;
        return dx * dx + dy * dy;
    }

    public float angle() {
        return (float) Math.atan2(y, x);
    }

    public float angleTo(Vec2 other) {
        return (float) Math.atan2(other.y - this.y, other.x - this.x);
    }

    // --- Array interop ---

    public float[] toArray() {
        return new float[]{x, y};
    }

    public float[] toArray(float[] out, int offset) {
        out[offset] = x;
        out[offset + 1] = y;
        return out;
    }

    public Vec2 fromArray(float[] arr, int offset) {
        this.x = arr[offset];
        this.y = arr[offset + 1];
        return this;
    }

    // --- Object overrides ---

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Vec2 other)) return false;
        return Float.compare(x, other.x) == 0 && Float.compare(y, other.y) == 0;
    }

    public boolean epsilonEquals(Vec2 other) {
        return MathUtil.epsilonEquals(x, other.x) && MathUtil.epsilonEquals(y, other.y);
    }

    public boolean epsilonEquals(Vec2 other, float tolerance) {
        return MathUtil.epsilonEquals(x, other.x, tolerance) && MathUtil.epsilonEquals(y, other.y, tolerance);
    }

    @Override
    public int hashCode() {
        int result = Float.hashCode(x);
        result = 31 * result + Float.hashCode(y);
        return result;
    }

    @Override
    public String toString() {
        return "Vec2(" + x + ", " + y + ")";
    }

    // --- HasGpuLayout ---

    @Override
    public GpuLayout<Vec2> defaultLayout() { return DEFAULT_LAYOUT; }

    /** Writes this vector into {@code dst} at {@code offset} using the default layout. */
    public void writeTo(MemorySegment dst, long offset) { DEFAULT_LAYOUT.writeTo(this, dst, offset); }

    /** Reads this vector from {@code src} at {@code offset} using the default layout. */
    public void readFrom(MemorySegment src, long offset) { DEFAULT_LAYOUT.readFrom(this, src, offset); }

    /** Writes this vector into {@code dst} at {@code offset} using an alternative layout. */
    public void writeTo(MemorySegment dst, long offset, GpuLayout<Vec2> layout) { layout.writeTo(this, dst, offset); }

    /** Reads this vector from {@code src} at {@code offset} using an alternative layout. */
    public void readFrom(MemorySegment src, long offset, GpuLayout<Vec2> layout) { layout.readFrom(this, src, offset); }

    // --- BuildStrategy ---

    public static void setBuildStrategy(BuildStrategy<Vec2> strategy) {
        buildStrategy = strategy;
    }

    public static BuildStrategy<Vec2> getBuildStrategy() {
        return buildStrategy;
    }

    // --- Builder ---

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private float x;
        private float y;
        private final boolean eager;

        public Builder() { this.eager = false; }
        private Builder(boolean eager) { this.eager = eager; }

        /** Returns a builder that will eagerly cache intermediate results (no-op currently). */
        public static Builder eager() { return new Builder(true); }
        /** Returns a builder that defers all computation to build() time (default). */
        public static Builder lazy() { return new Builder(false); }

        public Builder x(float x) { this.x = x; return this; }
        public Builder y(float y) { this.y = y; return this; }

        public Vec2 build() {
            Vec2 v = buildStrategy.obtain();
            v.x = this.x;
            v.y = this.y;
            return v;
        }

        public Vec2 build(BuildStrategy<Vec2> strategy) {
            Vec2 v = strategy.obtain();
            v.x = this.x;
            v.y = this.y;
            return v;
        }
    }
}
