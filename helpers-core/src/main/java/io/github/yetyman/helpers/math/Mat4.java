package io.github.yetyman.helpers.math;

import io.github.yetyman.vulkan.buffers.GpuLayout;
import io.github.yetyman.vulkan.buffers.HasGpuLayout;

import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT_UNALIGNED;

/**
 * Mutable 4x4 matrix in column-major order.
 * Field naming: mColumnRow (e.g. m00 = column 0, row 0; m30 = column 3, row 0 = translation X).
 */
public class Mat4 implements HasGpuLayout<Mat4> {

    // --- GPU Layouts ---

    /** Default layout: column-major (64 bytes). */
    public static final GpuLayout<Mat4> COLUMN_MAJOR = new GpuLayout<>() {
        @Override public int byteSize() { return 64; }
        @Override public void writeTo(Mat4 m, MemorySegment dst, long o) {
            dst.set(JAVA_FLOAT_UNALIGNED, o, m.m00);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 4, m.m01);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 8, m.m02);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 12, m.m03);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 16, m.m10);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 20, m.m11);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 24, m.m12);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 28, m.m13);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 32, m.m20);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 36, m.m21);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 40, m.m22);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 44, m.m23);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 48, m.m30);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 52, m.m31);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 56, m.m32);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 60, m.m33);
        }
        @Override public void readFrom(Mat4 m, MemorySegment src, long o) {
            m.m00 = src.get(JAVA_FLOAT_UNALIGNED, o);
            m.m01 = src.get(JAVA_FLOAT_UNALIGNED, o + 4);
            m.m02 = src.get(JAVA_FLOAT_UNALIGNED, o + 8);
            m.m03 = src.get(JAVA_FLOAT_UNALIGNED, o + 12);
            m.m10 = src.get(JAVA_FLOAT_UNALIGNED, o + 16);
            m.m11 = src.get(JAVA_FLOAT_UNALIGNED, o + 20);
            m.m12 = src.get(JAVA_FLOAT_UNALIGNED, o + 24);
            m.m13 = src.get(JAVA_FLOAT_UNALIGNED, o + 28);
            m.m20 = src.get(JAVA_FLOAT_UNALIGNED, o + 32);
            m.m21 = src.get(JAVA_FLOAT_UNALIGNED, o + 36);
            m.m22 = src.get(JAVA_FLOAT_UNALIGNED, o + 40);
            m.m23 = src.get(JAVA_FLOAT_UNALIGNED, o + 44);
            m.m30 = src.get(JAVA_FLOAT_UNALIGNED, o + 48);
            m.m31 = src.get(JAVA_FLOAT_UNALIGNED, o + 52);
            m.m32 = src.get(JAVA_FLOAT_UNALIGNED, o + 56);
            m.m33 = src.get(JAVA_FLOAT_UNALIGNED, o + 60);
        }
    };

    /** Canonical layout for this type. */
    public static final GpuLayout<Mat4> DEFAULT_LAYOUT = COLUMN_MAJOR;

    /** Row-major layout (64 bytes). */
    public static final GpuLayout<Mat4> ROW_MAJOR = new GpuLayout<>() {
        @Override public int byteSize() { return 64; }
        @Override public void writeTo(Mat4 m, MemorySegment dst, long o) {
            dst.set(JAVA_FLOAT_UNALIGNED, o, m.m00);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 4, m.m10);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 8, m.m20);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 12, m.m30);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 16, m.m01);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 20, m.m11);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 24, m.m21);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 28, m.m31);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 32, m.m02);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 36, m.m12);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 40, m.m22);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 44, m.m32);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 48, m.m03);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 52, m.m13);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 56, m.m23);
            dst.set(JAVA_FLOAT_UNALIGNED, o + 60, m.m33);
        }
        @Override public void readFrom(Mat4 m, MemorySegment src, long o) {
            m.m00 = src.get(JAVA_FLOAT_UNALIGNED, o);
            m.m10 = src.get(JAVA_FLOAT_UNALIGNED, o + 4);
            m.m20 = src.get(JAVA_FLOAT_UNALIGNED, o + 8);
            m.m30 = src.get(JAVA_FLOAT_UNALIGNED, o + 12);
            m.m01 = src.get(JAVA_FLOAT_UNALIGNED, o + 16);
            m.m11 = src.get(JAVA_FLOAT_UNALIGNED, o + 20);
            m.m21 = src.get(JAVA_FLOAT_UNALIGNED, o + 24);
            m.m31 = src.get(JAVA_FLOAT_UNALIGNED, o + 28);
            m.m02 = src.get(JAVA_FLOAT_UNALIGNED, o + 32);
            m.m12 = src.get(JAVA_FLOAT_UNALIGNED, o + 36);
            m.m22 = src.get(JAVA_FLOAT_UNALIGNED, o + 40);
            m.m32 = src.get(JAVA_FLOAT_UNALIGNED, o + 44);
            m.m03 = src.get(JAVA_FLOAT_UNALIGNED, o + 48);
            m.m13 = src.get(JAVA_FLOAT_UNALIGNED, o + 52);
            m.m23 = src.get(JAVA_FLOAT_UNALIGNED, o + 56);
            m.m33 = src.get(JAVA_FLOAT_UNALIGNED, o + 60);
        }
    };

    private static BuildStrategy<Mat4> buildStrategy = BuildStrategy.allocating(Mat4::new);

    // Column 0
    public float m00, m01, m02, m03;
    // Column 1
    public float m10, m11, m12, m13;
    // Column 2
    public float m20, m21, m22, m23;
    // Column 3
    public float m30, m31, m32, m33;

    /** Creates an identity matrix. */
    public Mat4() {
        setIdentity();
    }

    /** Copy constructor. */
    public Mat4(Mat4 other) {
        set(other);
    }

    /** Creates a matrix from a float array in column-major order (16 elements). */
    public Mat4(float[] values) {
        m00 = values[0];  m01 = values[1];  m02 = values[2];  m03 = values[3];
        m10 = values[4];  m11 = values[5];  m12 = values[6];  m13 = values[7];
        m20 = values[8];  m21 = values[9];  m22 = values[10]; m23 = values[11];
        m30 = values[12]; m31 = values[13]; m32 = values[14]; m33 = values[15];
    }

    /** Creates a matrix from four column vectors. */
    public Mat4(Vec4 col0, Vec4 col1, Vec4 col2, Vec4 col3) {
        m00 = col0.x; m01 = col0.y; m02 = col0.z; m03 = col0.w;
        m10 = col1.x; m11 = col1.y; m12 = col1.z; m13 = col1.w;
        m20 = col2.x; m21 = col2.y; m22 = col2.z; m23 = col2.w;
        m30 = col3.x; m31 = col3.y; m32 = col3.z; m33 = col3.w;
    }

    // --- Set ---

    public Mat4 setIdentity() {
        m00 = 1f; m01 = 0f; m02 = 0f; m03 = 0f;
        m10 = 0f; m11 = 1f; m12 = 0f; m13 = 0f;
        m20 = 0f; m21 = 0f; m22 = 1f; m23 = 0f;
        m30 = 0f; m31 = 0f; m32 = 0f; m33 = 1f;
        return this;
    }

    public Mat4 setZero() {
        m00 = 0f; m01 = 0f; m02 = 0f; m03 = 0f;
        m10 = 0f; m11 = 0f; m12 = 0f; m13 = 0f;
        m20 = 0f; m21 = 0f; m22 = 0f; m23 = 0f;
        m30 = 0f; m31 = 0f; m32 = 0f; m33 = 0f;
        return this;
    }

    public Mat4 set(Mat4 other) {
        m00 = other.m00; m01 = other.m01; m02 = other.m02; m03 = other.m03;
        m10 = other.m10; m11 = other.m11; m12 = other.m12; m13 = other.m13;
        m20 = other.m20; m21 = other.m21; m22 = other.m22; m23 = other.m23;
        m30 = other.m30; m31 = other.m31; m32 = other.m32; m33 = other.m33;
        return this;
    }

    // --- Static factories ---

    public static Mat4 identity() { return new Mat4(); }

    public static Mat4 zero() {
        Mat4 m = new Mat4();
        m.setZero();
        return m;
    }

    /**
     * Creates a Vulkan-compatible perspective projection matrix.
     * Y is flipped for Vulkan NDC (Y down), depth range 0..1.
     */
    public static Mat4 perspective(float fovY, float aspect, float near, float far) {
        Mat4 m = new Mat4();
        m.setZero();
        float tanHalfFov = (float) Math.tan(fovY * 0.5f);
        m.m00 = 1f / (aspect * tanHalfFov);
        m.m11 = -1f / tanHalfFov; // Y flip for Vulkan
        m.m22 = far / (near - far);
        m.m23 = -1f;
        m.m32 = (near * far) / (near - far);
        return m;
    }

    /**
     * Creates a Vulkan-compatible orthographic projection matrix.
     * Y is flipped for Vulkan NDC (Y down), depth range 0..1.
     */
    public static Mat4 orthographic(float left, float right, float bottom, float top, float near, float far) {
        Mat4 m = new Mat4();
        m.setZero();
        m.m00 = 2f / (right - left);
        m.m11 = -2f / (top - bottom); // Y flip for Vulkan
        m.m22 = -1f / (far - near);
        m.m30 = -(right + left) / (right - left);
        m.m31 = -(top + bottom) / (top - bottom);
        m.m32 = -near / (far - near);
        m.m33 = 1f;
        return m;
    }

    /**
     * Creates a look-at view matrix (right-handed).
     */
    public static Mat4 lookAt(Vec3 eye, Vec3 center, Vec3 up) {
        float fx = center.x - eye.x;
        float fy = center.y - eye.y;
        float fz = center.z - eye.z;
        // Normalize forward
        float fLen = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
        if (fLen > MathUtil.EPSILON) {
            float inv = 1f / fLen;
            fx *= inv; fy *= inv; fz *= inv;
        }
        // right = forward x up
        float rx = fy * up.z - fz * up.y;
        float ry = fz * up.x - fx * up.z;
        float rz = fx * up.y - fy * up.x;
        // Normalize right
        float rLen = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (rLen > MathUtil.EPSILON) {
            float inv = 1f / rLen;
            rx *= inv; ry *= inv; rz *= inv;
        }
        // recomputed up = right x forward
        float ux = ry * fz - rz * fy;
        float uy = rz * fx - rx * fz;
        float uz = rx * fy - ry * fx;

        Mat4 m = new Mat4();
        m.m00 = rx;  m.m10 = ry;  m.m20 = rz;  m.m30 = -(rx * eye.x + ry * eye.y + rz * eye.z);
        m.m01 = ux;  m.m11 = uy;  m.m21 = uz;  m.m31 = -(ux * eye.x + uy * eye.y + uz * eye.z);
        m.m02 = -fx; m.m12 = -fy; m.m22 = -fz; m.m32 = (fx * eye.x + fy * eye.y + fz * eye.z);
        m.m03 = 0f;  m.m13 = 0f;  m.m23 = 0f;  m.m33 = 1f;
        return m;
    }

    public static Mat4 translation(Vec3 v) {
        return translation(v.x, v.y, v.z);
    }

    public static Mat4 translation(float x, float y, float z) {
        Mat4 m = new Mat4();
        m.m30 = x; m.m31 = y; m.m32 = z;
        return m;
    }

    public static Mat4 scale(Vec3 v) {
        return scale(v.x, v.y, v.z);
    }

    public static Mat4 scale(float x, float y, float z) {
        Mat4 m = new Mat4();
        m.m00 = x; m.m11 = y; m.m22 = z;
        return m;
    }

    public static Mat4 scale(float uniform) {
        return scale(uniform, uniform, uniform);
    }

    public static Mat4 rotation(Vec3 axis, float angle) {
        Mat4 m = new Mat4();
        float len = axis.length();
        if (len < MathUtil.EPSILON) return m;
        float inv = 1f / len;
        float ax = axis.x * inv, ay = axis.y * inv, az = axis.z * inv;
        float c = (float) Math.cos(angle);
        float s = (float) Math.sin(angle);
        float t = 1f - c;

        m.m00 = t * ax * ax + c;
        m.m01 = t * ax * ay + s * az;
        m.m02 = t * ax * az - s * ay;

        m.m10 = t * ax * ay - s * az;
        m.m11 = t * ay * ay + c;
        m.m12 = t * ay * az + s * ax;

        m.m20 = t * ax * az + s * ay;
        m.m21 = t * ay * az - s * ax;
        m.m22 = t * az * az + c;

        m.m03 = 0f; m.m13 = 0f; m.m23 = 0f;
        m.m30 = 0f; m.m31 = 0f; m.m32 = 0f; m.m33 = 1f;
        return m;
    }

    public static Mat4 rotation(Quaternion q) {
        return q.toMat4();
    }

    public static Mat4 rotationY(float angle) {
        Mat4 m = new Mat4();
        float c = (float) Math.cos(angle);
        float s = (float) Math.sin(angle);
        m.m00 = c;  m.m02 = s;
        m.m20 = -s; m.m22 = c;
        m.m11 = 1f;
        m.m33 = 1f;
        return m;
    }

    public static Mat4 rotationX(float angle) {
        Mat4 m = new Mat4();
        float c = (float) Math.cos(angle);
        float s = (float) Math.sin(angle);
        m.m00 = 1f;
        m.m11 = c;  m.m12 = s;
        m.m21 = -s; m.m22 = c;
        m.m33 = 1f;
        return m;
    }

    /**
     * Creates a combined translation-rotation-scale matrix.
     */
    public static Mat4 trs(Vec3 pos, Quaternion rot, Vec3 scale) {
        Mat4 m = rot.toMat4();
        // Apply scale to rotation columns
        m.m00 *= scale.x; m.m01 *= scale.x; m.m02 *= scale.x;
        m.m10 *= scale.y; m.m11 *= scale.y; m.m12 *= scale.y;
        m.m20 *= scale.z; m.m21 *= scale.z; m.m22 *= scale.z;
        // Set translation
        m.m30 = pos.x; m.m31 = pos.y; m.m32 = pos.z;
        return m;
    }

    // --- Column/row access ---

    public Vec4 getColumn(int index) {
        return switch (index) {
            case 0 -> new Vec4(m00, m01, m02, m03);
            case 1 -> new Vec4(m10, m11, m12, m13);
            case 2 -> new Vec4(m20, m21, m22, m23);
            case 3 -> new Vec4(m30, m31, m32, m33);
            default -> throw new IndexOutOfBoundsException("Column index: " + index);
        };
    }

    public Mat4 setColumn(int index, Vec4 col) {
        switch (index) {
            case 0 -> { m00 = col.x; m01 = col.y; m02 = col.z; m03 = col.w; }
            case 1 -> { m10 = col.x; m11 = col.y; m12 = col.z; m13 = col.w; }
            case 2 -> { m20 = col.x; m21 = col.y; m22 = col.z; m23 = col.w; }
            case 3 -> { m30 = col.x; m31 = col.y; m32 = col.z; m33 = col.w; }
            default -> throw new IndexOutOfBoundsException("Column index: " + index);
        }
        return this;
    }

    public Vec4 getRow(int index) {
        return switch (index) {
            case 0 -> new Vec4(m00, m10, m20, m30);
            case 1 -> new Vec4(m01, m11, m21, m31);
            case 2 -> new Vec4(m02, m12, m22, m32);
            case 3 -> new Vec4(m03, m13, m23, m33);
            default -> throw new IndexOutOfBoundsException("Row index: " + index);
        };
    }

    public Mat4 setRow(int index, Vec4 row) {
        switch (index) {
            case 0 -> { m00 = row.x; m10 = row.y; m20 = row.z; m30 = row.w; }
            case 1 -> { m01 = row.x; m11 = row.y; m21 = row.z; m31 = row.w; }
            case 2 -> { m02 = row.x; m12 = row.y; m22 = row.z; m32 = row.w; }
            case 3 -> { m03 = row.x; m13 = row.y; m23 = row.z; m33 = row.w; }
            default -> throw new IndexOutOfBoundsException("Row index: " + index);
        }
        return this;
    }

    // --- Multiply ---

    /**
     * Multiplies this matrix by other in-place: this = this * other.
     */
    public Mat4 mul(Mat4 o) {
        float r00 = m00*o.m00 + m10*o.m01 + m20*o.m02 + m30*o.m03;
        float r01 = m01*o.m00 + m11*o.m01 + m21*o.m02 + m31*o.m03;
        float r02 = m02*o.m00 + m12*o.m01 + m22*o.m02 + m32*o.m03;
        float r03 = m03*o.m00 + m13*o.m01 + m23*o.m02 + m33*o.m03;

        float r10 = m00*o.m10 + m10*o.m11 + m20*o.m12 + m30*o.m13;
        float r11 = m01*o.m10 + m11*o.m11 + m21*o.m12 + m31*o.m13;
        float r12 = m02*o.m10 + m12*o.m11 + m22*o.m12 + m32*o.m13;
        float r13 = m03*o.m10 + m13*o.m11 + m23*o.m12 + m33*o.m13;

        float r20 = m00*o.m20 + m10*o.m21 + m20*o.m22 + m30*o.m23;
        float r21 = m01*o.m20 + m11*o.m21 + m21*o.m22 + m31*o.m23;
        float r22 = m02*o.m20 + m12*o.m21 + m22*o.m22 + m32*o.m23;
        float r23 = m03*o.m20 + m13*o.m21 + m23*o.m22 + m33*o.m23;

        float r30 = m00*o.m30 + m10*o.m31 + m20*o.m32 + m30*o.m33;
        float r31 = m01*o.m30 + m11*o.m31 + m21*o.m32 + m31*o.m33;
        float r32 = m02*o.m30 + m12*o.m31 + m22*o.m32 + m32*o.m33;
        float r33 = m03*o.m30 + m13*o.m31 + m23*o.m32 + m33*o.m33;

        m00=r00; m01=r01; m02=r02; m03=r03;
        m10=r10; m11=r11; m12=r12; m13=r13;
        m20=r20; m21=r21; m22=r22; m23=r23;
        m30=r30; m31=r31; m32=r32; m33=r33;
        return this;
    }

    public Mat4 mulNew(Mat4 o) {
        Mat4 r = new Mat4();
        r.m00 = m00*o.m00 + m10*o.m01 + m20*o.m02 + m30*o.m03;
        r.m01 = m01*o.m00 + m11*o.m01 + m21*o.m02 + m31*o.m03;
        r.m02 = m02*o.m00 + m12*o.m01 + m22*o.m02 + m32*o.m03;
        r.m03 = m03*o.m00 + m13*o.m01 + m23*o.m02 + m33*o.m03;

        r.m10 = m00*o.m10 + m10*o.m11 + m20*o.m12 + m30*o.m13;
        r.m11 = m01*o.m10 + m11*o.m11 + m21*o.m12 + m31*o.m13;
        r.m12 = m02*o.m10 + m12*o.m11 + m22*o.m12 + m32*o.m13;
        r.m13 = m03*o.m10 + m13*o.m11 + m23*o.m12 + m33*o.m13;

        r.m20 = m00*o.m20 + m10*o.m21 + m20*o.m22 + m30*o.m23;
        r.m21 = m01*o.m20 + m11*o.m21 + m21*o.m22 + m31*o.m23;
        r.m22 = m02*o.m20 + m12*o.m21 + m22*o.m22 + m32*o.m23;
        r.m23 = m03*o.m20 + m13*o.m21 + m23*o.m22 + m33*o.m23;

        r.m30 = m00*o.m30 + m10*o.m31 + m20*o.m32 + m30*o.m33;
        r.m31 = m01*o.m30 + m11*o.m31 + m21*o.m32 + m31*o.m33;
        r.m32 = m02*o.m30 + m12*o.m31 + m22*o.m32 + m32*o.m33;
        r.m33 = m03*o.m30 + m13*o.m31 + m23*o.m32 + m33*o.m33;
        return r;
    }

    /**
     * Transforms a Vec4 by this matrix: result = this * v.
     */
    public Vec4 mulVec4(Vec4 v) {
        return new Vec4(
                m00*v.x + m10*v.y + m20*v.z + m30*v.w,
                m01*v.x + m11*v.y + m21*v.z + m31*v.w,
                m02*v.x + m12*v.y + m22*v.z + m32*v.w,
                m03*v.x + m13*v.y + m23*v.z + m33*v.w
        );
    }

    /**
     * Transforms a Vec3 with implicit w=1, returns Vec3 after perspective divide.
     */
    public Vec3 mulVec3(Vec3 v) {
        float rx = m00*v.x + m10*v.y + m20*v.z + m30;
        float ry = m01*v.x + m11*v.y + m21*v.z + m31;
        float rz = m02*v.x + m12*v.y + m22*v.z + m32;
        float rw = m03*v.x + m13*v.y + m23*v.z + m33;
        if (Math.abs(rw) > MathUtil.EPSILON) {
            float inv = 1f / rw;
            return new Vec3(rx * inv, ry * inv, rz * inv);
        }
        return new Vec3(rx, ry, rz);
    }

    /**
     * Transforms a point (w=1), returns Vec3 without perspective divide.
     */
    public Vec3 transformPoint(Vec3 v) {
        return new Vec3(
                m00*v.x + m10*v.y + m20*v.z + m30,
                m01*v.x + m11*v.y + m21*v.z + m31,
                m02*v.x + m12*v.y + m22*v.z + m32
        );
    }

    /**
     * Transforms a direction (w=0), returns Vec3.
     */
    public Vec3 transformDirection(Vec3 v) {
        return new Vec3(
                m00*v.x + m10*v.y + m20*v.z,
                m01*v.x + m11*v.y + m21*v.z,
                m02*v.x + m12*v.y + m22*v.z
        );
    }

    // --- Transpose ---

    public Mat4 transpose() {
        float t;
        t = m01; m01 = m10; m10 = t;
        t = m02; m02 = m20; m20 = t;
        t = m03; m03 = m30; m30 = t;
        t = m12; m12 = m21; m21 = t;
        t = m13; m13 = m31; m31 = t;
        t = m23; m23 = m32; m32 = t;
        return this;
    }

    public Mat4 transposeNew() {
        Mat4 r = new Mat4();
        r.m00 = m00; r.m01 = m10; r.m02 = m20; r.m03 = m30;
        r.m10 = m01; r.m11 = m11; r.m12 = m21; r.m13 = m31;
        r.m20 = m02; r.m21 = m12; r.m22 = m22; r.m23 = m32;
        r.m30 = m03; r.m31 = m13; r.m32 = m23; r.m33 = m33;
        return r;
    }

    // --- Determinant ---

    public float determinant() {
        float a = m00 * (m11*(m22*m33 - m23*m32) - m21*(m12*m33 - m13*m32) + m31*(m12*m23 - m13*m22));
        float b = m10 * (m01*(m22*m33 - m23*m32) - m21*(m02*m33 - m03*m32) + m31*(m02*m23 - m03*m22));
        float c = m20 * (m01*(m12*m33 - m13*m32) - m11*(m02*m33 - m03*m32) + m31*(m02*m13 - m03*m12));
        float d = m30 * (m01*(m12*m23 - m13*m22) - m11*(m02*m23 - m03*m22) + m21*(m02*m13 - m03*m12));
        return a - b + c - d;
    }

    // --- Inverse ---

    /**
     * Inverts this matrix in-place. If singular, sets to identity.
     */
    public Mat4 inverse() {
        float det = determinant();
        if (Math.abs(det) < MathUtil.EPSILON) {
            setIdentity();
            return this;
        }
        float invDet = 1f / det;

        float t00 = (m11*(m22*m33 - m23*m32) - m21*(m12*m33 - m13*m32) + m31*(m12*m23 - m13*m22)) * invDet;
        float t01 = -(m01*(m22*m33 - m23*m32) - m21*(m02*m33 - m03*m32) + m31*(m02*m23 - m03*m22)) * invDet;
        float t02 = (m01*(m12*m33 - m13*m32) - m11*(m02*m33 - m03*m32) + m31*(m02*m13 - m03*m12)) * invDet;
        float t03 = -(m01*(m12*m23 - m13*m22) - m11*(m02*m23 - m03*m22) + m21*(m02*m13 - m03*m12)) * invDet;

        float t10 = -(m10*(m22*m33 - m23*m32) - m20*(m12*m33 - m13*m32) + m30*(m12*m23 - m13*m22)) * invDet;
        float t11 = (m00*(m22*m33 - m23*m32) - m20*(m02*m33 - m03*m32) + m30*(m02*m23 - m03*m22)) * invDet;
        float t12 = -(m00*(m12*m33 - m13*m32) - m10*(m02*m33 - m03*m32) + m30*(m02*m13 - m03*m12)) * invDet;
        float t13 = (m00*(m12*m23 - m13*m22) - m10*(m02*m23 - m03*m22) + m20*(m02*m13 - m03*m12)) * invDet;

        float t20 = (m10*(m21*m33 - m23*m31) - m20*(m11*m33 - m13*m31) + m30*(m11*m23 - m13*m21)) * invDet;
        float t21 = -(m00*(m21*m33 - m23*m31) - m20*(m01*m33 - m03*m31) + m30*(m01*m23 - m03*m21)) * invDet;
        float t22 = (m00*(m11*m33 - m13*m31) - m10*(m01*m33 - m03*m31) + m30*(m01*m13 - m03*m11)) * invDet;
        float t23 = -(m00*(m11*m23 - m13*m21) - m10*(m01*m23 - m03*m21) + m20*(m01*m13 - m03*m11)) * invDet;

        float t30 = -(m10*(m21*m32 - m22*m31) - m20*(m11*m32 - m12*m31) + m30*(m11*m22 - m12*m21)) * invDet;
        float t31 = (m00*(m21*m32 - m22*m31) - m20*(m01*m32 - m02*m31) + m30*(m01*m22 - m02*m21)) * invDet;
        float t32 = -(m00*(m11*m32 - m12*m31) - m10*(m01*m32 - m02*m31) + m30*(m01*m12 - m02*m11)) * invDet;
        float t33 = (m00*(m11*m22 - m12*m21) - m10*(m01*m22 - m02*m21) + m20*(m01*m12 - m02*m11)) * invDet;

        m00=t00; m01=t01; m02=t02; m03=t03;
        m10=t10; m11=t11; m12=t12; m13=t13;
        m20=t20; m21=t21; m22=t22; m23=t23;
        m30=t30; m31=t31; m32=t32; m33=t33;
        return this;
    }

    public Mat4 inverseNew() {
        return new Mat4(this).inverse();
    }

    // --- Decompose ---

    /**
     * Decomposes this TRS matrix into position, rotation, and scale.
     * Assumes the matrix was built via translation * rotation * scale.
     * @param outPos receives position (column 3 xyz)
     * @param outRot receives rotation as quaternion
     * @param outScale receives scale (column lengths)
     */
    public void decompose(Vec3 outPos, Quaternion outRot, Vec3 outScale) {
        // Extract translation
        outPos.x = m30;
        outPos.y = m31;
        outPos.z = m32;

        // Extract scale (column vector lengths)
        float sx = (float) Math.sqrt(m00*m00 + m01*m01 + m02*m02);
        float sy = (float) Math.sqrt(m10*m10 + m11*m11 + m12*m12);
        float sz = (float) Math.sqrt(m20*m20 + m21*m21 + m22*m22);
        outScale.x = sx;
        outScale.y = sy;
        outScale.z = sz;

        // Extract rotation (divide columns by scale)
        float isx = sx > MathUtil.EPSILON ? 1f / sx : 0f;
        float isy = sy > MathUtil.EPSILON ? 1f / sy : 0f;
        float isz = sz > MathUtil.EPSILON ? 1f / sz : 0f;

        // Build a pure rotation Mat3
        Mat3 rot = new Mat3();
        rot.m00 = m00 * isx; rot.m01 = m01 * isx; rot.m02 = m02 * isx;
        rot.m10 = m10 * isy; rot.m11 = m11 * isy; rot.m12 = m12 * isy;
        rot.m20 = m20 * isz; rot.m21 = m21 * isz; rot.m22 = m22 * isz;

        Quaternion q = Quaternion.fromMat3(rot);
        outRot.x = q.x;
        outRot.y = q.y;
        outRot.z = q.z;
        outRot.w = q.w;
    }

    // --- Array interop ---

    public float[] toArray() {
        return new float[]{
                m00, m01, m02, m03,
                m10, m11, m12, m13,
                m20, m21, m22, m23,
                m30, m31, m32, m33
        };
    }

    public float[] toArray(float[] out, int offset) {
        out[offset]    = m00; out[offset+1]  = m01; out[offset+2]  = m02; out[offset+3]  = m03;
        out[offset+4]  = m10; out[offset+5]  = m11; out[offset+6]  = m12; out[offset+7]  = m13;
        out[offset+8]  = m20; out[offset+9]  = m21; out[offset+10] = m22; out[offset+11] = m23;
        out[offset+12] = m30; out[offset+13] = m31; out[offset+14] = m32; out[offset+15] = m33;
        return out;
    }

    public Mat4 fromArray(float[] arr, int offset) {
        m00 = arr[offset];    m01 = arr[offset+1];  m02 = arr[offset+2];  m03 = arr[offset+3];
        m10 = arr[offset+4];  m11 = arr[offset+5];  m12 = arr[offset+6];  m13 = arr[offset+7];
        m20 = arr[offset+8];  m21 = arr[offset+9];  m22 = arr[offset+10]; m23 = arr[offset+11];
        m30 = arr[offset+12]; m31 = arr[offset+13]; m32 = arr[offset+14]; m33 = arr[offset+15];
        return this;
    }

    // --- Object overrides ---

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Mat4 o)) return false;
        return Float.compare(m00, o.m00) == 0 && Float.compare(m01, o.m01) == 0
            && Float.compare(m02, o.m02) == 0 && Float.compare(m03, o.m03) == 0
            && Float.compare(m10, o.m10) == 0 && Float.compare(m11, o.m11) == 0
            && Float.compare(m12, o.m12) == 0 && Float.compare(m13, o.m13) == 0
            && Float.compare(m20, o.m20) == 0 && Float.compare(m21, o.m21) == 0
            && Float.compare(m22, o.m22) == 0 && Float.compare(m23, o.m23) == 0
            && Float.compare(m30, o.m30) == 0 && Float.compare(m31, o.m31) == 0
            && Float.compare(m32, o.m32) == 0 && Float.compare(m33, o.m33) == 0;
    }

    @Override
    public int hashCode() {
        int result = Float.hashCode(m00);
        result = 31 * result + Float.hashCode(m01);
        result = 31 * result + Float.hashCode(m02);
        result = 31 * result + Float.hashCode(m03);
        result = 31 * result + Float.hashCode(m10);
        result = 31 * result + Float.hashCode(m11);
        result = 31 * result + Float.hashCode(m12);
        result = 31 * result + Float.hashCode(m13);
        result = 31 * result + Float.hashCode(m20);
        result = 31 * result + Float.hashCode(m21);
        result = 31 * result + Float.hashCode(m22);
        result = 31 * result + Float.hashCode(m23);
        result = 31 * result + Float.hashCode(m30);
        result = 31 * result + Float.hashCode(m31);
        result = 31 * result + Float.hashCode(m32);
        result = 31 * result + Float.hashCode(m33);
        return result;
    }

    @Override
    public String toString() {
        return "Mat4[\n"
             + "  " + m00 + ", " + m10 + ", " + m20 + ", " + m30 + "\n"
             + "  " + m01 + ", " + m11 + ", " + m21 + ", " + m31 + "\n"
             + "  " + m02 + ", " + m12 + ", " + m22 + ", " + m32 + "\n"
             + "  " + m03 + ", " + m13 + ", " + m23 + ", " + m33 + "\n"
             + "]";
    }

    // --- HasGpuLayout ---

    @Override
    public GpuLayout<Mat4> defaultLayout() { return DEFAULT_LAYOUT; }

    /** Writes this matrix into {@code dst} at {@code offset} using the default layout. */
    public void writeTo(MemorySegment dst, long offset) { DEFAULT_LAYOUT.writeTo(this, dst, offset); }

    /** Reads this matrix from {@code src} at {@code offset} using the default layout. */
    public void readFrom(MemorySegment src, long offset) { DEFAULT_LAYOUT.readFrom(this, src, offset); }

    /** Writes this matrix into {@code dst} at {@code offset} using an alternative layout. */
    public void writeTo(MemorySegment dst, long offset, GpuLayout<Mat4> layout) { layout.writeTo(this, dst, offset); }

    /** Reads this matrix from {@code src} at {@code offset} using an alternative layout. */
    public void readFrom(MemorySegment src, long offset, GpuLayout<Mat4> layout) { layout.readFrom(this, src, offset); }

    // --- BuildStrategy ---

    public static void setBuildStrategy(BuildStrategy<Mat4> strategy) {
        buildStrategy = strategy;
    }

    public static BuildStrategy<Mat4> getBuildStrategy() {
        return buildStrategy;
    }

    // --- Builder ---

    public static Mat4Builder builder() {
        return new Mat4Builder();
    }

    /**
     * Builder with memoization for perspective, lookAt, and TRS construction.
     * Designed to be allocated once and reused across frames.
     */
    public static class Mat4Builder {
        private final boolean eager;

        // Perspective state
        private float fov, aspect, near, far;
        private float cachedTanHalfFov = Float.NaN;
        private float lastFov = Float.NaN;

        // LookAt state
        private final Vec3 eye = new Vec3();
        private final Vec3 center = new Vec3();
        private final Vec3 up = new Vec3(0, 1, 0);
        private float cachedFx, cachedFy, cachedFz;
        private float cachedRx, cachedRy, cachedRz;
        private float cachedUx, cachedUy, cachedUz;
        private boolean lookAtBasisDirty = true;
        private float lastCenterX = Float.NaN, lastCenterY = Float.NaN, lastCenterZ = Float.NaN;
        private float lastUpX = Float.NaN, lastUpY = Float.NaN, lastUpZ = Float.NaN;

        // TRS state
        private final Vec3 pos = new Vec3();
        private final Quaternion rot = new Quaternion();
        private final Vec3 scale = new Vec3(1, 1, 1);
        private final Mat4 cachedRotMatrix = new Mat4();
        private boolean rotDirty = true;
        private float lastQx = Float.NaN, lastQy = Float.NaN, lastQz = Float.NaN, lastQw = Float.NaN;

        public Mat4Builder() { this.eager = false; }
        private Mat4Builder(boolean eager) { this.eager = eager; }

        /** Returns a builder that will eagerly cache intermediate results (no-op currently). */
        public static Mat4Builder eager() { return new Mat4Builder(true); }
        /** Returns a builder that defers all computation to build() time (default). */
        public static Mat4Builder lazy() { return new Mat4Builder(false); }

        // --- Perspective ---

        public Mat4Builder perspective(float fov, float aspect, float near, float far) {
            this.fov = fov;
            this.aspect = aspect;
            this.near = near;
            this.far = far;
            return this;
        }

        public Mat4 buildPerspective() {
            if (fov != lastFov) {
                cachedTanHalfFov = (float) Math.tan(fov * 0.5f);
                lastFov = fov;
            }
            Mat4 m = buildStrategy.obtain();
            m.setZero();
            m.m00 = 1f / (aspect * cachedTanHalfFov);
            m.m11 = -1f / cachedTanHalfFov;
            m.m22 = far / (near - far);
            m.m23 = -1f;
            m.m32 = (near * far) / (near - far);
            return m;
        }

        // --- LookAt ---

        public Mat4Builder lookAt(Vec3 eye, Vec3 center, Vec3 up) {
            this.eye.set(eye);
            if (center.x != lastCenterX || center.y != lastCenterY || center.z != lastCenterZ
                    || up.x != lastUpX || up.y != lastUpY || up.z != lastUpZ) {
                this.center.set(center);
                this.up.set(up);
                lastCenterX = center.x; lastCenterY = center.y; lastCenterZ = center.z;
                lastUpX = up.x; lastUpY = up.y; lastUpZ = up.z;
                lookAtBasisDirty = true;
            }
            return this;
        }

        public Mat4 buildLookAt() {
            if (lookAtBasisDirty) {
                float fx = center.x - eye.x;
                float fy = center.y - eye.y;
                float fz = center.z - eye.z;
                float fLen = (float) Math.sqrt(fx*fx + fy*fy + fz*fz);
                if (fLen > MathUtil.EPSILON) { float inv = 1f/fLen; fx *= inv; fy *= inv; fz *= inv; }
                cachedFx = fx; cachedFy = fy; cachedFz = fz;
                float rx = fy*up.z - fz*up.y;
                float ry = fz*up.x - fx*up.z;
                float rz = fx*up.y - fy*up.x;
                float rLen = (float) Math.sqrt(rx*rx + ry*ry + rz*rz);
                if (rLen > MathUtil.EPSILON) { float inv = 1f/rLen; rx *= inv; ry *= inv; rz *= inv; }
                cachedRx = rx; cachedRy = ry; cachedRz = rz;
                cachedUx = ry*fz - rz*fy;
                cachedUy = rz*fx - rx*fz;
                cachedUz = rx*fy - ry*fx;
                lookAtBasisDirty = false;
            }
            Mat4 m = buildStrategy.obtain();
            m.m00 = cachedRx; m.m10 = cachedRy; m.m20 = cachedRz;
            m.m30 = -(cachedRx*eye.x + cachedRy*eye.y + cachedRz*eye.z);
            m.m01 = cachedUx; m.m11 = cachedUy; m.m21 = cachedUz;
            m.m31 = -(cachedUx*eye.x + cachedUy*eye.y + cachedUz*eye.z);
            m.m02 = -cachedFx; m.m12 = -cachedFy; m.m22 = -cachedFz;
            m.m32 = (cachedFx*eye.x + cachedFy*eye.y + cachedFz*eye.z);
            m.m03 = 0f; m.m13 = 0f; m.m23 = 0f; m.m33 = 1f;
            return m;
        }

        // --- TRS ---

        public Mat4Builder pos(Vec3 pos) { this.pos.set(pos); return this; }
        public Mat4Builder rot(Quaternion rot) {
            if (rot.x != lastQx || rot.y != lastQy || rot.z != lastQz || rot.w != lastQw) {
                this.rot.x = rot.x; this.rot.y = rot.y; this.rot.z = rot.z; this.rot.w = rot.w;
                lastQx = rot.x; lastQy = rot.y; lastQz = rot.z; lastQw = rot.w;
                rotDirty = true;
            }
            return this;
        }
        public Mat4Builder scale(Vec3 scale) { this.scale.set(scale); return this; }

        public Mat4 buildTrs() {
            if (rotDirty) {
                Mat4 rm = rot.toMat4();
                cachedRotMatrix.set(rm);
                rotDirty = false;
            }
            Mat4 m = buildStrategy.obtain();
            m.set(cachedRotMatrix);
            m.m00 *= scale.x; m.m01 *= scale.x; m.m02 *= scale.x;
            m.m10 *= scale.y; m.m11 *= scale.y; m.m12 *= scale.y;
            m.m20 *= scale.z; m.m21 *= scale.z; m.m22 *= scale.z;
            m.m30 = pos.x; m.m31 = pos.y; m.m32 = pos.z;
            m.m03 = 0f; m.m13 = 0f; m.m23 = 0f; m.m33 = 1f;
            return m;
        }

        public Mat4 build() {
            return buildStrategy.obtain();
        }

        public Mat4 build(BuildStrategy<Mat4> strategy) {
            return strategy.obtain();
        }
    }
}
