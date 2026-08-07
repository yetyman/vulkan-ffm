package io.github.yetyman.vulkan.mesh.source;

import io.github.yetyman.helpers.math.Vec3;
import io.github.yetyman.helpers.math.geometry.AABB;
import io.github.yetyman.helpers.math.spatial.isosurface.MeshOutput;
import io.github.yetyman.vulkan.mesh.AttributeFormat;
import io.github.yetyman.vulkan.mesh.AttributeSemantic;
import io.github.yetyman.vulkan.mesh.DeviceRange;
import io.github.yetyman.vulkan.mesh.IndexWidth;
import io.github.yetyman.vulkan.mesh.MeshLayout;
import io.github.yetyman.vulkan.mesh.PrimitiveTopology;

import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED;

/**
 * Adapts a {@link MeshOutput} (from isosurface extraction) as a {@link GeometrySource}.
 *
 * <p>This is the designated early validation case from the plan: if this adapter is awkward to write,
 * the interface is wrong. As it turns out, it is trivial: the adapter exposes POSITION as a
 * generated attribute stream that writes directly from the {@code List<Vec3>} into the destination,
 * and indices as a generated index stream that writes from the {@code List<Integer>}.
 *
 * <p>The source is always host-readable, has no native layout (data is in Java collections, not a
 * packed native buffer), and is not device-resident.
 */
public final class MeshOutputSource implements GeometrySource {

    private final MeshOutput output;
    private final AABB bounds;
    private final float[] normals; // 3 floats per vertex, computed from face normals

    /**
     * @param output the isosurface extraction result to adapt
     */
    public MeshOutputSource(MeshOutput output) {
        this.output = output;
        this.bounds = computeBounds(output.vertices());
        this.normals = computeSmoothNormals(output.vertices(), output.indices());
    }

    @Override
    public Set<AttributeSemantic> available() {
        return Set.of(AttributeSemantic.POSITION, AttributeSemantic.NORMAL);
    }

    @Override
    public AttributeStream stream(AttributeSemantic semantic) {
        if (semantic == AttributeSemantic.POSITION) return positionStream;
        if (semantic == AttributeSemantic.NORMAL) return normalStream;
        throw new IllegalArgumentException("MeshOutputSource provides POSITION and NORMAL, not '" + semantic + "'");
    }

    @Override
    public Optional<IndexStream> indices() {
        if (output.indexCount() == 0) return Optional.empty();
        return Optional.of(indexStream);
    }

    @Override
    public long elementCount() {
        return output.vertexCount();
    }

    @Override
    public PrimitiveTopology topology() {
        return PrimitiveTopology.TRIANGLE_LIST;
    }

    @Override
    public AABB bounds() {
        return bounds;
    }

    /**
     * No native layout: data lives in Java collections, not a packed native segment. The upload
     * path will always use per-attribute transcoding rather than the flat-copy identity path.
     */
    @Override
    public Optional<MeshLayout> nativeLayout() {
        return Optional.empty();
    }

    // --- Position stream: generates directly from List<Vec3> into the destination ---

    private final AttributeStream positionStream = new AttributeStream() {
        @Override public AttributeSemantic semantic() { return AttributeSemantic.POSITION; }
        @Override public AttributeFormat sourceFormat() { return AttributeFormat.F32x3; }
        @Override public long elementCount() { return output.vertexCount(); }
        @Override public Residency residency() { return Residency.HOST; }
        @Override public boolean isHostReadable() { return true; }
        @Override public Optional<DeviceRange> deviceRange() { return Optional.empty(); }

        @Override
        public void transcodeInto(MeshLayout targetLayout, MemorySegment dst, long dstOffset,
                                  long dstStride, long firstElement, long elementCount) {
            List<Vec3> verts = output.vertices();
            long pos = dstOffset;
            int first = (int) firstElement;
            int count = (int) elementCount;
            for (int i = first; i < first + count; i++) {
                Vec3 v = verts.get(i);
                dst.set(JAVA_FLOAT_UNALIGNED, pos, v.x);
                dst.set(JAVA_FLOAT_UNALIGNED, pos + 4, v.y);
                dst.set(JAVA_FLOAT_UNALIGNED, pos + 8, v.z);
                pos += dstStride;
            }
        }
    };

    // --- Normal stream: smooth normals computed from face normals ---

    private final AttributeStream normalStream = new AttributeStream() {
        @Override public AttributeSemantic semantic() { return AttributeSemantic.NORMAL; }
        @Override public AttributeFormat sourceFormat() { return AttributeFormat.F32x3; }
        @Override public long elementCount() { return output.vertexCount(); }
        @Override public Residency residency() { return Residency.HOST; }
        @Override public boolean isHostReadable() { return true; }
        @Override public Optional<DeviceRange> deviceRange() { return Optional.empty(); }

        @Override
        public void transcodeInto(MeshLayout targetLayout, MemorySegment dst, long dstOffset,
                                  long dstStride, long firstElement, long elementCount) {
            long pos = dstOffset;
            int first = (int) firstElement;
            int count = (int) elementCount;
            for (int i = first; i < first + count; i++) {
                dst.set(JAVA_FLOAT_UNALIGNED, pos, normals[i * 3]);
                dst.set(JAVA_FLOAT_UNALIGNED, pos + 4, normals[i * 3 + 1]);
                dst.set(JAVA_FLOAT_UNALIGNED, pos + 8, normals[i * 3 + 2]);
                pos += dstStride;
            }
        }
    };

    // --- Index stream: generates directly from List<Integer> into the destination ---

    private final IndexStream indexStream = new IndexStream() {
        @Override public IndexWidth sourceWidth() { return IndexWidth.U32; }
        @Override public long indexCount() { return output.indexCount(); }
        @Override public Residency residency() { return Residency.HOST; }
        @Override public boolean isHostReadable() { return true; }
        @Override public Optional<DeviceRange> deviceRange() { return Optional.empty(); }

        @Override
        public void transcodeInto(IndexWidth targetWidth, long vertexBaseOffset,
                                  MemorySegment dst, long dstOffset,
                                  long firstIndex, long indexCount) {
            List<Integer> indices = output.indices();
            long pos = dstOffset;
            int first = (int) firstIndex;
            int count = (int) indexCount;
            int dstByte = targetWidth.byteSize();
            for (int i = first; i < first + count; i++) {
                long value = Integer.toUnsignedLong(indices.get(i)) + vertexBaseOffset;
                switch (targetWidth) {
                    case U8 -> dst.set(java.lang.foreign.ValueLayout.JAVA_BYTE, pos, (byte) value);
                    case U16 -> dst.set(java.lang.foreign.ValueLayout.JAVA_SHORT_UNALIGNED, pos, (short) value);
                    case U32 -> dst.set(JAVA_INT_UNALIGNED, pos, (int) value);
                }
                pos += dstByte;
            }
        }
    };

    private static AABB computeBounds(List<Vec3> vertices) {
        if (vertices.isEmpty()) return new AABB(new Vec3(), new Vec3());
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (Vec3 v : vertices) {
            if (v.x < minX) minX = v.x; if (v.x > maxX) maxX = v.x;
            if (v.y < minY) minY = v.y; if (v.y > maxY) maxY = v.y;
            if (v.z < minZ) minZ = v.z; if (v.z > maxZ) maxZ = v.z;
        }
        return new AABB(new Vec3(minX, minY, minZ), new Vec3(maxX, maxY, maxZ));
    }

    /**
     * Computes smooth per-vertex normals by accumulating face normals for each vertex then
     * normalizing. This produces correct smooth-shaded normals for isosurface meshes where
     * vertices are shared between triangles.
     */
    private static float[] computeSmoothNormals(List<Vec3> vertices, List<Integer> indices) {
        float[] normals = new float[vertices.size() * 3];
        // Accumulate face normals
        for (int i = 0; i + 2 < indices.size(); i += 3) {
            int i0 = indices.get(i);
            int i1 = indices.get(i + 1);
            int i2 = indices.get(i + 2);
            Vec3 v0 = vertices.get(i0);
            Vec3 v1 = vertices.get(i1);
            Vec3 v2 = vertices.get(i2);
            // edge vectors
            float e1x = v1.x - v0.x, e1y = v1.y - v0.y, e1z = v1.z - v0.z;
            float e2x = v2.x - v0.x, e2y = v2.y - v0.y, e2z = v2.z - v0.z;
            // cross product (face normal, not normalized — area-weighted)
            float nx = e1y * e2z - e1z * e2y;
            float ny = e1z * e2x - e1x * e2z;
            float nz = e1x * e2y - e1y * e2x;
            // accumulate to each vertex of the face
            normals[i0 * 3] += nx; normals[i0 * 3 + 1] += ny; normals[i0 * 3 + 2] += nz;
            normals[i1 * 3] += nx; normals[i1 * 3 + 1] += ny; normals[i1 * 3 + 2] += nz;
            normals[i2 * 3] += nx; normals[i2 * 3 + 1] += ny; normals[i2 * 3 + 2] += nz;
        }
        // Normalize
        for (int i = 0; i < normals.length; i += 3) {
            float x = normals[i], y = normals[i + 1], z = normals[i + 2];
            float len = (float) Math.sqrt(x * x + y * y + z * z);
            if (len > 1e-8f) {
                normals[i] = x / len;
                normals[i + 1] = y / len;
                normals[i + 2] = z / len;
            }
        }
        return normals;
    }
}
