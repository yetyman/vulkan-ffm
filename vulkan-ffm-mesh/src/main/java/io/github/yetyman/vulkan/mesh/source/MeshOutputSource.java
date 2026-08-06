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

    /**
     * @param output the isosurface extraction result to adapt
     */
    public MeshOutputSource(MeshOutput output) {
        this.output = output;
        this.bounds = computeBounds(output.vertices());
    }

    @Override
    public Set<AttributeSemantic> available() {
        return Set.of(AttributeSemantic.POSITION);
    }

    @Override
    public AttributeStream stream(AttributeSemantic semantic) {
        if (semantic == AttributeSemantic.POSITION) return positionStream;
        throw new IllegalArgumentException("MeshOutputSource only provides POSITION, not '" + semantic + "'");
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
}
