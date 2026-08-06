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
 * A position-only subdivided grid on an arbitrary plane. Unlike {@link PlaneSource} this carries no
 * normals or texture coordinates, making it suitable for point clouds, wireframe debug grids,
 * terrain heightmaps (where the Y is filled later), and physics collision meshes.
 *
 * <p>The grid spans from {@code min} to {@code max} with the given resolution on each axis.
 * All Y values start at zero; a consumer fills them from a heightmap or other source.
 *
 * <p>Vertex count: {@code (resX + 1) * (resZ + 1)}. Index count: {@code resX * resZ * 6}.
 */
public final class GridSource implements GeometrySource {

    private final SegmentGeometrySource delegate;

    /**
     * @param arena arena owning the backing memory
     * @param minX  X extent start
     * @param maxX  X extent end
     * @param minZ  Z extent start
     * @param maxZ  Z extent end
     * @param resX  subdivisions along X
     * @param resZ  subdivisions along Z
     */
    public GridSource(Arena arena, float minX, float maxX, float minZ, float maxZ,
                      int resX, int resZ) {
        if (resX < 1 || resZ < 1) throw new IllegalArgumentException("resolution must be >= 1");

        int vertsX = resX + 1;
        int vertsZ = resZ + 1;
        int vertexCount = vertsX * vertsZ;
        int indexCount = resX * resZ * 6;

        MeshLayout layout = MeshLayout.builder()
                .stream(0)
                .attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3)
                .build();

        long stride = layout.strideOf(0);
        MemorySegment verts = arena.allocate(stride * vertexCount);
        MemorySegment idxs = arena.allocate((long) indexCount * 4);

        float widthX = maxX - minX;
        float widthZ = maxZ - minZ;

        int vi = 0;
        for (int z = 0; z < vertsZ; z++) {
            float pz = minZ + ((float) z / resZ) * widthZ;
            for (int x = 0; x < vertsX; x++) {
                float px = minX + ((float) x / resX) * widthX;
                long o = (long) vi * stride;
                verts.set(JAVA_FLOAT_UNALIGNED, o, px);
                verts.set(JAVA_FLOAT_UNALIGNED, o + 4, 0f);
                verts.set(JAVA_FLOAT_UNALIGNED, o + 8, pz);
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
                .bounds(new AABB(new Vec3(minX, 0, minZ), new Vec3(maxX, 0, maxZ)))
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
