package io.github.yetyman.vulkan.mesh.source.primitives;

import io.github.yetyman.helpers.math.Vec3;
import io.github.yetyman.helpers.math.geometry.AABB;
import io.github.yetyman.vulkan.mesh.AttributeFormat;
import io.github.yetyman.vulkan.mesh.AttributeSemantic;
import io.github.yetyman.vulkan.mesh.IndexWidth;
import io.github.yetyman.vulkan.mesh.MeshLayout;
import io.github.yetyman.vulkan.mesh.PrimitiveTopology;
import io.github.yetyman.vulkan.mesh.source.AttributeStream;
import io.github.yetyman.vulkan.mesh.source.GeometrySource;
import io.github.yetyman.vulkan.mesh.source.IndexStream;
import io.github.yetyman.vulkan.mesh.source.SegmentGeometrySource;
import io.github.yetyman.vulkan.mesh.source.SegmentIndexStream;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Optional;
import java.util.Set;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED;

/**
 * A subdivided XZ plane centered at origin. Positions, normals (all +Y), and texture coordinates.
 *
 * <p>Vertex count: {@code (resX + 1) * (resZ + 1)}. Index count: {@code resX * resZ * 6}.
 *
 * @see GridSource for an arbitrary-axis grid without normals/UVs
 */
public final class PlaneSource implements GeometrySource {

    private final SegmentGeometrySource delegate;

    /**
     * @param arena arena owning the backing memory
     * @param width extent along X
     * @param depth extent along Z
     * @param resX  subdivisions along X
     * @param resZ  subdivisions along Z
     */
    public PlaneSource(Arena arena, float width, float depth, int resX, int resZ) {
        if (resX < 1 || resZ < 1) throw new IllegalArgumentException("resolution must be >= 1");

        int vertsX = resX + 1;
        int vertsZ = resZ + 1;
        int vertexCount = vertsX * vertsZ;
        int indexCount = resX * resZ * 6;

        MeshLayout layout = MeshLayout.builder()
                .stream(0)
                .attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3)
                .attribute(AttributeSemantic.NORMAL, AttributeFormat.F32x3)
                .attribute(AttributeSemantic.TEXCOORD(0), AttributeFormat.F32x2)
                .build();

        long stride = layout.strideOf(0);
        MemorySegment verts = arena.allocate(stride * vertexCount);
        MemorySegment idxs = arena.allocate((long) indexCount * 4);

        float halfW = width * 0.5f;
        float halfD = depth * 0.5f;

        int vi = 0;
        for (int z = 0; z < vertsZ; z++) {
            float tz = (float) z / resZ;
            float pz = -halfD + tz * depth;
            for (int x = 0; x < vertsX; x++) {
                float tx = (float) x / resX;
                float px = -halfW + tx * width;
                long o = (long) vi * stride;
                verts.set(JAVA_FLOAT_UNALIGNED, o, px);
                verts.set(JAVA_FLOAT_UNALIGNED, o + 4, 0f);
                verts.set(JAVA_FLOAT_UNALIGNED, o + 8, pz);
                verts.set(JAVA_FLOAT_UNALIGNED, o + 12, 0f);
                verts.set(JAVA_FLOAT_UNALIGNED, o + 16, 1f);
                verts.set(JAVA_FLOAT_UNALIGNED, o + 20, 0f);
                verts.set(JAVA_FLOAT_UNALIGNED, o + 24, tx);
                verts.set(JAVA_FLOAT_UNALIGNED, o + 28, tz);
                vi++;
            }
        }

        int ii = 0;
        for (int z = 0; z < resZ; z++) {
            for (int x = 0; x < resX; x++) {
                int tl = z * vertsX + x;
                int tr = tl + 1;
                int bl = tl + vertsX;
                int br = bl + 1;
                idxs.set(JAVA_INT_UNALIGNED, (long) ii * 4, tl);
                idxs.set(JAVA_INT_UNALIGNED, (long) (ii + 1) * 4, bl);
                idxs.set(JAVA_INT_UNALIGNED, (long) (ii + 2) * 4, tr);
                idxs.set(JAVA_INT_UNALIGNED, (long) (ii + 3) * 4, tr);
                idxs.set(JAVA_INT_UNALIGNED, (long) (ii + 4) * 4, bl);
                idxs.set(JAVA_INT_UNALIGNED, (long) (ii + 5) * 4, br);
                ii += 6;
            }
        }

        delegate = SegmentGeometrySource.builder()
                .layout(layout)
                .elementCount(vertexCount)
                .topology(PrimitiveTopology.TRIANGLE_LIST)
                .bounds(new AABB(new Vec3(-halfW, 0, -halfD), new Vec3(halfW, 0, halfD)))
                .streamData(0, verts)
                .indices(IndexWidth.U32, indexCount, idxs)
                .build();
    }

    @Override public Set<AttributeSemantic> available() { return delegate.available(); }
    @Override public AttributeStream stream(AttributeSemantic s) { return delegate.stream(s); }
    @Override public Optional<IndexStream> indices() { return delegate.indices(); }
    @Override public long elementCount() { return delegate.elementCount(); }
    @Override public PrimitiveTopology topology() { return delegate.topology(); }
    @Override public AABB bounds() { return delegate.bounds(); }
    @Override public Optional<MeshLayout> nativeLayout() { return delegate.nativeLayout(); }
}
