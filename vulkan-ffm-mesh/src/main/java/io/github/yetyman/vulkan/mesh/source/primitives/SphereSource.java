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
 * A UV sphere centered at origin with configurable radius, rings, and sectors.
 * Positions, normals, and texture coordinates.
 *
 * <p>Vertex count: {@code (rings + 1) * (sectors + 1)}.
 * Index count: {@code rings * sectors * 6} (degenerate triangles at poles are included for
 * simplicity; the GPU clips them for free).
 */
public final class SphereSource implements GeometrySource {

    private final SegmentGeometrySource delegate;

    /**
     * @param arena   arena owning the backing memory
     * @param radius  sphere radius
     * @param rings   latitude divisions (>= 2)
     * @param sectors longitude divisions (>= 3)
     */
    public SphereSource(Arena arena, float radius, int rings, int sectors) {
        if (rings < 2) throw new IllegalArgumentException("rings must be >= 2");
        if (sectors < 3) throw new IllegalArgumentException("sectors must be >= 3");

        int vertsPerRow = sectors + 1;
        int vertexCount = (rings + 1) * vertsPerRow;
        int indexCount = rings * sectors * 6;

        MeshLayout layout = MeshLayout.builder()
                .stream(0)
                .attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3)
                .attribute(AttributeSemantic.NORMAL, AttributeFormat.F32x3)
                .attribute(AttributeSemantic.TEXCOORD(0), AttributeFormat.F32x2)
                .build();

        long stride = layout.strideOf(0);
        MemorySegment verts = arena.allocate(stride * vertexCount);
        MemorySegment idxs = arena.allocate((long) indexCount * 4);

        // Generate vertices
        int vi = 0;
        for (int r = 0; r <= rings; r++) {
            float v = (float) r / rings;
            float phi = (float) (Math.PI * r / rings);
            float sinPhi = (float) Math.sin(phi);
            float cosPhi = (float) Math.cos(phi);
            for (int s = 0; s <= sectors; s++) {
                float u = (float) s / sectors;
                float theta = (float) (2.0 * Math.PI * s / sectors);
                float sinTheta = (float) Math.sin(theta);
                float cosTheta = (float) Math.cos(theta);

                float nx = sinPhi * cosTheta;
                float ny = cosPhi;
                float nz = sinPhi * sinTheta;

                long o = (long) vi * stride;
                verts.set(JAVA_FLOAT_UNALIGNED, o, nx * radius);
                verts.set(JAVA_FLOAT_UNALIGNED, o + 4, ny * radius);
                verts.set(JAVA_FLOAT_UNALIGNED, o + 8, nz * radius);
                verts.set(JAVA_FLOAT_UNALIGNED, o + 12, nx);
                verts.set(JAVA_FLOAT_UNALIGNED, o + 16, ny);
                verts.set(JAVA_FLOAT_UNALIGNED, o + 20, nz);
                verts.set(JAVA_FLOAT_UNALIGNED, o + 24, u);
                verts.set(JAVA_FLOAT_UNALIGNED, o + 28, v);
                vi++;
            }
        }

        // Generate indices
        int ii = 0;
        for (int r = 0; r < rings; r++) {
            for (int s = 0; s < sectors; s++) {
                int tl = r * vertsPerRow + s;
                int tr = tl + 1;
                int bl = tl + vertsPerRow;
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
                .bounds(new AABB(new Vec3(-radius, -radius, -radius), new Vec3(radius, radius, radius)))
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
