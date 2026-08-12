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
 * A torus (donut) centered at origin in the XZ plane with configurable major and minor radii,
 * ring count, and side count. Provides positions, normals, and texture coordinates.
 *
 * <p>Vertex count: {@code (rings + 1) * (sides + 1)}.
 * Index count: {@code rings * sides * 6}.
 *
 * @see SphereSource
 * @see BoxSource
 */
public final class TorusSource implements GeometrySource {

    private final SegmentGeometrySource delegate;

    /**
     * Creates a torus with the given parameters and unit defaults.
     *
     * @param arena       arena owning the backing memory
     * @param majorRadius distance from the center of the torus to the center of the tube
     * @param minorRadius radius of the tube
     * @param rings       number of divisions around the main axis (>= 3)
     * @param sides       number of divisions around the tube cross-section (>= 3)
     */
    public TorusSource(Arena arena, float majorRadius, float minorRadius, int rings, int sides) {
        if (rings < 3) throw new IllegalArgumentException("rings must be >= 3");
        if (sides < 3) throw new IllegalArgumentException("sides must be >= 3");

        int vertsPerRing = sides + 1;
        int vertexCount = (rings + 1) * vertsPerRing;
        int indexCount = rings * sides * 6;

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
            float u = (float) r / rings;
            float theta = (float) (2.0 * Math.PI * r / rings);
            float cosTheta = (float) Math.cos(theta);
            float sinTheta = (float) Math.sin(theta);

            for (int s = 0; s <= sides; s++) {
                float v = (float) s / sides;
                float phi = (float) (2.0 * Math.PI * s / sides);
                float cosPhi = (float) Math.cos(phi);
                float sinPhi = (float) Math.sin(phi);

                // Position: rotate the tube circle around the main axis (Y)
                float x = (majorRadius + minorRadius * cosPhi) * cosTheta;
                float y = minorRadius * sinPhi;
                float z = (majorRadius + minorRadius * cosPhi) * sinTheta;

                // Normal: direction from the ring center to the vertex
                float nx = cosPhi * cosTheta;
                float ny = sinPhi;
                float nz = cosPhi * sinTheta;

                long o = (long) vi * stride;
                verts.set(JAVA_FLOAT_UNALIGNED, o, x);
                verts.set(JAVA_FLOAT_UNALIGNED, o + 4, y);
                verts.set(JAVA_FLOAT_UNALIGNED, o + 8, z);
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
            for (int s = 0; s < sides; s++) {
                int tl = r * vertsPerRing + s;
                int tr = tl + 1;
                int bl = tl + vertsPerRing;
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

        float outerRadius = majorRadius + minorRadius;
        delegate = SegmentGeometrySource.builder()
                .layout(layout)
                .elementCount(vertexCount)
                .topology(PrimitiveTopology.TRIANGLE_LIST)
                .bounds(new AABB(
                        new Vec3(-outerRadius, -minorRadius, -outerRadius),
                        new Vec3(outerRadius, minorRadius, outerRadius)))
                .streamData(0, verts)
                .indices(IndexWidth.U32, indexCount, idxs)
                .build();
    }

    /**
     * Creates a torus with default radii (major=1.0, minor=0.4) and 32 rings x 24 sides.
     */
    public TorusSource(Arena arena) {
        this(arena, 1.0f, 0.4f, 32, 24);
    }

    @Override public Set<AttributeSemantic> available() { return delegate.available(); }
    @Override public AttributeStream stream(AttributeSemantic s) { return delegate.stream(s); }
    @Override public Optional<IndexStream> indices() { return delegate.indices(); }
    @Override public long elementCount() { return delegate.elementCount(); }
    @Override public PrimitiveTopology topology() { return delegate.topology(); }
    @Override public AABB bounds() { return delegate.bounds(); }
    @Override public Optional<MeshLayout> nativeLayout() { return delegate.nativeLayout(); }
}
