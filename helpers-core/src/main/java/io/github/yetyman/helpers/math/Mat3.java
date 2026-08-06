package io.github.yetyman.helpers.math;

import io.github.yetyman.vulkan.buffers.GpuLayout;
import io.github.yetyman.vulkan.buffers.HasGpuLayout;

import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT_UNALIGNED;

/**
 * Mutable 3x3 matrix in column-major order.
 * Field naming: mColumnRow (e.g. m00 = column 0, row 0; m10 = column 1, row 0).
 */
public class Mat3 implements HasGpuLayout<Mat3> {

    // --- GPU Layouts ---

    /** Default layout: column-major, packed (36 bytes). */
    public static final GpuLayout<Mat3> COLUMN_MAJOR = new GpuLayout<>() {
        @Override public int byteSize() { return 36; }
        @Override public void writeTo(Mat3 m, MemorySegment dst, long o) {
            dst.set(JAVA_FLOAT_UNALIGNED, o, m.m00);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 4, m.m01);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 8, m.m02);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 12, m.m10);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 16, m.m11);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 20, m.m12);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 24, m.m20);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 28, m.m21);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 32, m.m22);
        }
        @Override public void readFrom(Mat3 m, MemorySegment src, long o) {
            m.m00 = src.get(JAVA_FLOAT_UNALIGNED, o);
            m.m01 = src.get(JAVA_FLOAT_UNALIGNED, o + 4);
            m.m02 = src.get(JAVA_FLOAT_UNALIGNED, o + 8);
            m.m10 = src.get(JAVA_FLOAT_UNALIGNED, o + 12);
            m.m11 = src.get(JAVA_FLOAT_UNALIGNED, o + 16);
            m.m12 = src.get(JAVA_FLOAT_UNALIGNED, o + 20);
            m.m20 = src.get(JAVA_FLOAT_UNALIGNED, o + 24);
            m.m21 = src.get(JAVA_FLOAT_UNALIGNED, o + 28);
            m.m22 = src.get(JAVA_FLOAT_UNALIGNED, o + 32);
        }
    };

    /** Canonical layout for this type. */
    public static final GpuLayout<Mat3> DEFAULT_LAYOUT = COLUMN_MAJOR;

    /** std140-aligned layout: each column padded to 16 bytes (48 bytes total). */
    public static final GpuLayout<Mat3> STD140 = new GpuLayout<>() {
        @Override public int byteSize() { return 48; }
        @Override public void writeTo(Mat3 m, MemorySegment dst, long o) {
            dst.set(JAVA_FLOAT_UNALIGNED, o, m.m00);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 4, m.m01);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 8, m.m02);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 12, 0f);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 16, m.m10);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 20, m.m11);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 24, m.m12);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 28, 0f);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 32, m.m20);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 36, m.m21);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 40, m.m22);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 44, 0f);
        }
        @Override public void readFrom(Mat3 m, MemorySegment src, long o) {
            m.m00 = src.get(JAVA_FLOAT_UNALIGNED, o);
            m.m01 = src.get(JAVA_FLOAT_UNALIGNED, o + 4);
            m.m02 = src.get(JAVA_FLOAT_UNALIGNED, o + 8);
            m.m10 = src.get(JAVA_FLOAT_UNALIGNED, o + 16);
            m.m11 = src.get(JAVA_FLOAT_UNALIGNED, o + 20);
            m.m12 = src.get(JAVA_FLOAT_UNALIGNED, o + 24);
            m.m20 = src.get(JAVA_FLOAT_UNALIGNED, o + 32);
            m.m21 = src.get(JAVA_FLOAT_UNALIGNED, o + 36);
            m.m22 = src.get(JAVA_FLOAT_UNALIGNED, o + 40);
        }
    };

    /** Row-major layout (36 bytes). */
    public static final GpuLayout<Mat3> ROW_MAJOR = new GpuLayout<>() {
        @Override public int byteSize() { return 36; }
        @Override public void writeTo(Mat3 m, MemorySegment dst, long o) {
            dst.set(JAVA_FLOAT_UNALIGNED, o, m.m00);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 4, m.m10);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 8, m.m20);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 12, m.m01);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 16, m.m11);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 20, m.m21);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 24, m.m02);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 28, m.m12);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 32, m.m22);
        }
        @Override public void readFrom(Mat3 m, MemorySegment src, long o) {
            m.m00 = src.get(JAVA_FLOAT_UNALIGNED, o);
            m.m10 = src.get(JAVA_FLOAT_UNALIGNED, o + 4);
            m.m20 = src.get(JAVA_FLOAT_UNALIGNED, o + 8);
            m.m01 = src.get(JAVA_FLOAT_UNALIGNED, o + 12);
            m.m11 = src.get(JAVA_FLOAT_UNALIGNED, o + 16);
            m.m21 = src.get(JAVA_FLOAT_UNALIGNED, o + 20);
            m.m02 = src.get(JAVA_FLOAT_UNALIGNED, o + 24);
            m.m12 = src.get(JAVA_FLOAT_UNALIGNED, o + 28);
            m.m22 = src.get(JAVA_FLOAT_UNALIGNED, o + 32);
        }
    };

    private static BuildStrategy<Mat3> buildStrategy = BuildStrategy.allocating(Mat3::new);

    // Column 0
    public float m00, m01, m02;
    // Column 1
    public float m10, m11, m12;
    // Column 2
    public float m20, m21, m22;

    /** Creates an identity matrix. */
    public Mat3() {
        identity();
    }

    /** Creates a matrix from column vectors. */
    public Mat3(Vec3 col0, Vec3 col1, Vec3 col2) {
        m00 = col0.x; m01 = col0.y; m02 = col0.z;
        m10 = col1.x; m11 = col1.y; m12 = col1.z;
        m20 = col2.x; m21 = col2.y; m22 = col2.z;
    }

    /** Creates a matrix from a float array in column-major order. */
    public Mat3(float[] values) {
        m00 = values[0]; m01 = values[1]; m02 = values[2];
        m10 = values[3]; m11 = values[4]; m12 = values[5];
        m20 = values[6]; m21 = values[7]; m22 = values[8];
    }

    /** Copy constructor. */
    public Mat3(Mat3 other) {
        m00 = other.m00; m01 = other.m01; m02 = other.m02;
        m10 = other.m10; m11 = other.m11; m12 = other.m12;
        m20 = other.m20; m21 = other.m21; m22 = other.m22;
    }

    // --- Static factories ---

    public static Mat3 identity() {
        Mat3 m = new Mat3();
        m.m00 = 1f; m.m01 = 0f; m.m02 = 0f;
        m.m10 = 0f; m.m11 = 1f; m.m12 = 0f;
        m.m20 = 0f; m.m21 = 0f; m.m22 = 1f;
        return m;
    }

    public static Mat3 zero() {
        Mat3 m = new Mat3();
        m.m00 = 0f; m.m01 = 0f; m.m02 = 0f;
        m.m10 = 0f; m.m11 = 0f; m.m12 = 0f;
        m.m20 = 0f; m.m21 = 0f; m.m22 = 0f;
        return m;
    }

    // --- Set to identity ---

    public Mat3 setIdentity() {
        m00 = 1f; m01 = 0f; m02 = 0f;
        m10 = 0f; m11 = 1f; m12 = 0f;
        m20 = 0f; m21 = 0f; m22 = 1f;
        return this;
    }

    public Mat3 set(Mat3 other) {
        m00 = other.m00; m01 = other.m01; m02 = other.m02;
        m10 = other.m10; m11 = other.m11; m12 = other.m12;
        m20 = other.m20; m21 = other.m21; m22 = other.m22;
        return this;
    }

    // --- Column/row access ---

    public Vec3 getColumn(int index) {
        return switch (index) {
            case 0 -> new Vec3(m00, m01, m02);
            case 1 -> new Vec3(m10, m11, m12);
            case 2 -> new Vec3(m20, m21, m22);
            default -> throw new IndexOutOfBoundsException("Column index: " + index);
        };
    }

    public Mat3 setColumn(int index, Vec3 col) {
        switch (index) {
            case 0 -> { m00 = col.x; m01 = col.y; m02 = col.z; }
            case 1 -> { m10 = col.x; m11 = col.y; m12 = col.z; }
            case 2 -> { m20 = col.x; m21 = col.y; m22 = col.z; }
            default -> throw new IndexOutOfBoundsException("Column index: " + index);
        }
        return this;
    }

    public Vec3 getRow(int index) {
        return switch (index) {
            case 0 -> new Vec3(m00, m10, m20);
            case 1 -> new Vec3(m01, m11, m21);
            case 2 -> new Vec3(m02, m12, m22);
            default -> throw new IndexOutOfBoundsException("Row index: " + index);
        };
    }

    public Mat3 setRow(int index, Vec3 row) {
        switch (index) {
            case 0 -> { m00 = row.x; m10 = row.y; m20 = row.z; }
            case 1 -> { m01 = row.x; m11 = row.y; m21 = row.z; }
            case 2 -> { m02 = row.x; m12 = row.y; m22 = row.z; }
            default -> throw new IndexOutOfBoundsException("Row index: " + index);
        }
        return this;
    }

    // --- Multiply ---

    /**
     * Multiplies this matrix by other in-place: this = this * other.
     */
    public Mat3 mul(Mat3 other) {
        float r00 = m00 * other.m00 + m10 * other.m01 + m20 * other.m02;
        float r01 = m01 * other.m00 + m11 * other.m01 + m21 * other.m02;
        float r02 = m02 * other.m00 + m12 * other.m01 + m22 * other.m02;

        float r10 = m00 * other.m10 + m10 * other.m11 + m20 * other.m12;
        float r11 = m01 * other.m10 + m11 * other.m11 + m21 * other.m12;
        float r12 = m02 * other.m10 + m12 * other.m11 + m22 * other.m12;

        float r20 = m00 * other.m20 + m10 * other.m21 + m20 * other.m22;
        float r21 = m01 * other.m20 + m11 * other.m21 + m21 * other.m22;
        float r22 = m02 * other.m20 + m12 * other.m21 + m22 * other.m22;

        m00 = r00; m01 = r01; m02 = r02;
        m10 = r10; m11 = r11; m12 = r12;
        m20 = r20; m21 = r21; m22 = r22;
        return this;
    }

    public Mat3 mulNew(Mat3 other) {
        Mat3 r = new Mat3();
        r.m00 = m00 * other.m00 + m10 * other.m01 + m20 * other.m02;
        r.m01 = m01 * other.m00 + m11 * other.m01 + m21 * other.m02;
        r.m02 = m02 * other.m00 + m12 * other.m01 + m22 * other.m02;

        r.m10 = m00 * other.m10 + m10 * other.m11 + m20 * other.m12;
        r.m11 = m01 * other.m10 + m11 * other.m11 + m21 * other.m12;
        r.m12 = m02 * other.m10 + m12 * other.m11 + m22 * other.m12;

        r.m20 = m00 * other.m20 + m10 * other.m21 + m20 * other.m22;
        r.m21 = m01 * other.m20 + m11 * other.m21 + m21 * other.m22;
        r.m22 = m02 * other.m20 + m12 * other.m21 + m22 * other.m22;
        return r;
    }

    /**
     * Transforms a Vec3 by this matrix: result = this * v.
     */
    public Vec3 mulVec3(Vec3 v) {
        return new Vec3(
                m00 * v.x + m10 * v.y + m20 * v.z,
                m01 * v.x + m11 * v.y + m21 * v.z,
                m02 * v.x + m12 * v.y + m22 * v.z
        );
    }

    /**
     * Transforms a Vec3 by this matrix in-place: v = this * v.
     */
    public Vec3 mulVec3(Vec3 v, Vec3 dest) {
        float rx = m00 * v.x + m10 * v.y + m20 * v.z;
        float ry = m01 * v.x + m11 * v.y + m21 * v.z;
        float rz = m02 * v.x + m12 * v.y + m22 * v.z;
        dest.x = rx;
        dest.y = ry;
        dest.z = rz;
        return dest;
    }

    // --- Transpose ---

    public Mat3 transpose() {
        float t;
        t = m01; m01 = m10; m10 = t;
        t = m02; m02 = m20; m20 = t;
        t = m12; m12 = m21; m21 = t;
        return this;
    }

    public Mat3 transposeNew() {
        Mat3 r = new Mat3();
        r.m00 = m00; r.m01 = m10; r.m02 = m20;
        r.m10 = m01; r.m11 = m11; r.m12 = m21;
        r.m20 = m02; r.m21 = m12; r.m22 = m22;
        return r;
    }

    // --- Determinant ---

    public float determinant() {
        return m00 * (m11 * m22 - m12 * m21)
             - m10 * (m01 * m22 - m02 * m21)
             + m20 * (m01 * m12 - m02 * m11);
    }

    // --- Inverse ---

    /**
     * Inverts this matrix in-place. Returns this.
     * If the matrix is singular (det ~0), sets to identity.
     */
    public Mat3 inverse() {
        float det = determinant();
        if (Math.abs(det) < MathUtil.EPSILON) {
            setIdentity();
            return this;
        }
        float invDet = 1f / det;

        float r00 = (m11 * m22 - m12 * m21) * invDet;
        float r01 = (m02 * m21 - m01 * m22) * invDet;
        float r02 = (m01 * m12 - m02 * m11) * invDet;
        float r10 = (m12 * m20 - m10 * m22) * invDet;
        float r11 = (m00 * m22 - m02 * m20) * invDet;
        float r12 = (m02 * m10 - m00 * m12) * invDet;
        float r20 = (m10 * m21 - m11 * m20) * invDet;
        float r21 = (m01 * m20 - m00 * m21) * invDet;
        float r22 = (m00 * m11 - m01 * m10) * invDet;

        m00 = r00; m01 = r01; m02 = r02;
        m10 = r10; m11 = r11; m12 = r12;
        m20 = r20; m21 = r21; m22 = r22;
        return this;
    }

    public Mat3 inverseNew() {
        return new Mat3(this).inverse();
    }

    // --- Array interop ---

    /**
     * Writes this matrix into a float array in column-major order.
     */
    public float[] toArray() {
        return new float[]{m00, m01, m02, m10, m11, m12, m20, m21, m22};
    }

    public float[] toArray(float[] out, int offset) {
        out[offset]     = m00; out[offset + 1] = m01; out[offset + 2] = m02;
        out[offset + 3] = m10; out[offset + 4] = m11; out[offset + 5] = m12;
        out[offset + 6] = m20; out[offset + 7] = m21; out[offset + 8] = m22;
        return out;
    }

    public Mat3 fromArray(float[] arr, int offset) {
        m00 = arr[offset];     m01 = arr[offset + 1]; m02 = arr[offset + 2];
        m10 = arr[offset + 3]; m11 = arr[offset + 4]; m12 = arr[offset + 5];
        m20 = arr[offset + 6]; m21 = arr[offset + 7]; m22 = arr[offset + 8];
        return this;
    }

    // --- Object overrides ---

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Mat3 other)) return false;
        return Float.compare(m00, other.m00) == 0 && Float.compare(m01, other.m01) == 0 && Float.compare(m02, other.m02) == 0
            && Float.compare(m10, other.m10) == 0 && Float.compare(m11, other.m11) == 0 && Float.compare(m12, other.m12) == 0
            && Float.compare(m20, other.m20) == 0 && Float.compare(m21, other.m21) == 0 && Float.compare(m22, other.m22) == 0;
    }

    @Override
    public int hashCode() {
        int result = Float.hashCode(m00);
        result = 31 * result + Float.hashCode(m01);
        result = 31 * result + Float.hashCode(m02);
        result = 31 * result + Float.hashCode(m10);
        result = 31 * result + Float.hashCode(m11);
        result = 31 * result + Float.hashCode(m12);
        result = 31 * result + Float.hashCode(m20);
        result = 31 * result + Float.hashCode(m21);
        result = 31 * result + Float.hashCode(m22);
        return result;
    }

    @Override
    public String toString() {
        return "Mat3[\n"
             + "  " + m00 + ", " + m10 + ", " + m20 + "\n"
             + "  " + m01 + ", " + m11 + ", " + m21 + "\n"
             + "  " + m02 + ", " + m12 + ", " + m22 + "\n"
             + "]";
    }

    // --- HasGpuLayout ---

    @Override
    public GpuLayout<Mat3> defaultLayout() { return DEFAULT_LAYOUT; }

    /** Writes this matrix into {@code dst} at {@code offset} using the default layout. */
    public void writeTo(MemorySegment dst, long offset) { DEFAULT_LAYOUT.writeTo(this, dst, offset); }

    /** Reads this matrix from {@code src} at {@code offset} using the default layout. */
    public void readFrom(MemorySegment src, long offset) { DEFAULT_LAYOUT.readFrom(this, src, offset); }

    /** Writes this matrix into {@code dst} at {@code offset} using an alternative layout. */
    public void writeTo(MemorySegment dst, long offset, GpuLayout<Mat3> layout) { layout.writeTo(this, dst, offset); }

    /** Reads this matrix from {@code src} at {@code offset} using an alternative layout. */
    public void readFrom(MemorySegment src, long offset, GpuLayout<Mat3> layout) { layout.readFrom(this, src, offset); }

    // --- BuildStrategy ---

    public static void setBuildStrategy(BuildStrategy<Mat3> strategy) {
        buildStrategy = strategy;
    }

    public static BuildStrategy<Mat3> getBuildStrategy() {
        return buildStrategy;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private float[] values;
        private Vec3 col0, col1, col2;
        private final boolean eager;

        public Builder() { this.eager = false; }
        private Builder(boolean eager) { this.eager = eager; }

        /** Returns a builder that will eagerly cache intermediate results (no-op currently). */
        public static Builder eager() { return new Builder(true); }
        /** Returns a builder that defers all computation to build() time (default). */
        public static Builder lazy() { return new Builder(false); }

        /** Build from three column vectors. */
        public Builder columns(Vec3 col0, Vec3 col1, Vec3 col2) {
            this.col0 = col0; this.col1 = col1; this.col2 = col2;
            this.values = null;
            return this;
        }

        /** Build from a float array in column-major order. */
        public Builder fromArray(float[] values) {
            this.values = values;
            this.col0 = null;
            return this;
        }

        /** Build an identity matrix. */
        public Builder identity() {
            this.values = null;
            this.col0 = null;
            return this;
        }

        public Mat3 build() {
            Mat3 m = buildStrategy.obtain();
            return apply(m);
        }

        public Mat3 build(BuildStrategy<Mat3> strategy) {
            Mat3 m = strategy.obtain();
            return apply(m);
        }

        // NOTE: apply() could be invoked eagerly to cache intermediate results when parameters change,
        // but is not yet implemented that way. Currently all computation happens at build() time.
        private Mat3 apply(Mat3 m) {
            if (values != null) {
                m.fromArray(values, 0);
            } else if (col0 != null) {
                m.setColumn(0, col0);
                m.setColumn(1, col1);
                m.setColumn(2, col2);
            } else {
                m.setIdentity();
            }
            return m;
        }
    }
}
