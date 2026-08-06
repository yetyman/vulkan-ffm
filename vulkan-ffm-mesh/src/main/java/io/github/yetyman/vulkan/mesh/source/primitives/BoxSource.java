package io.github.yetyman.vulkan.mesh.source.primitives;

import io.github.yetyman.helpers.math.Vec3;
import io.github.yetyman.helpers.math.geometry.AABB;
import io.github.yetyman.vulkan.mesh.AttributeFormat;
import io.github.yetyman.vulkan.mesh.AttributeSemantic;
import io.github.yetyman.vulkan.mesh.IndexWidth;
import io.github.yetyman.vulkan.mesh.MeshLayout;
import io.github.yetyman.vulkan.mesh.PrimitiveTopology;
import io.github.yetyman.vulkan.mesh.source.GeometrySource;
import io.github.yetyman.vulkan.mesh.source.IndexStream;
import io.github.yetyman.vulkan.mesh.source.SegmentGeometrySource;
import io.github.yetyman.vulkan.mesh.source.SegmentIndexStream;
import io.github.yetyman.vulkan.mesh.source.AttributeStream;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Optional;
import java.util.Set;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_SHORT_UNALIGNED;

/**
 * A unit box centered at origin (half-extents 0.5 on each axis), with positions, normals, and
 * texture coordinates. 24 vertices (4 per face with unique normals), 36 indices.
 *
 * <p>The box is generated into an arena-owned segment at construction time. Cheap to create,
 * universally useful for tests and samples.
 */
public final class BoxSource implements GeometrySource {

    private static final int VERTEX_COUNT = 24;
    private static final int INDEX_COUNT = 36;

    private final SegmentGeometrySource delegate;

    /**
     * Creates a unit box.
     *
     * @param arena arena to allocate backing memory in; the source is valid for its lifetime
     */
    public BoxSource(Arena arena) {
        this(arena, new Vec3(-0.5f, -0.5f, -0.5f), new Vec3(0.5f, 0.5f, 0.5f));
    }

    /**
     * Creates a box with the given min/max corners.
     *
     * @param arena arena to allocate backing memory in
     * @param min   minimum corner
     * @param max   maximum corner
     */
    public BoxSource(Arena arena, Vec3 min, Vec3 max) {
        MeshLayout layout = MeshLayout.builder()
                .stream(0)
                .attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3)
                .attribute(AttributeSemantic.NORMAL, AttributeFormat.F32x3)
                .attribute(AttributeSemantic.TEXCOORD(0), AttributeFormat.F32x2)
                .build();

        long stride = layout.strideOf(0);
        MemorySegment verts = arena.allocate(stride * VERTEX_COUNT);
        MemorySegment idxs = arena.allocate(INDEX_COUNT * 2L);

        generateBox(verts, stride, idxs, min, max);

        delegate = SegmentGeometrySource.builder()
                .layout(layout)
                .elementCount(VERTEX_COUNT)
                .topology(PrimitiveTopology.TRIANGLE_LIST)
                .bounds(new AABB(new Vec3(min), new Vec3(max)))
                .streamData(0, verts)
                .indices(IndexWidth.U16, INDEX_COUNT, idxs)
                .build();
    }

    @Override public Set<AttributeSemantic> available() { return delegate.available(); }
    @Override public AttributeStream stream(AttributeSemantic s) { return delegate.stream(s); }
    @Override public Optional<IndexStream> indices() { return delegate.indices(); }
    @Override public long elementCount() { return delegate.elementCount(); }
    @Override public PrimitiveTopology topology() { return delegate.topology(); }
    @Override public AABB bounds() { return delegate.bounds(); }
    @Override public Optional<MeshLayout> nativeLayout() { return delegate.nativeLayout(); }

    private static void generateBox(MemorySegment verts, long stride, MemorySegment idxs,
                                    Vec3 min, Vec3 max) {
        // Faces: +X, -X, +Y, -Y, +Z, -Z
        float[][] normals = {
                {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
        };
        // For each face: 4 corner positions
        float[][][] corners = {
                // +X
                {{max.x, min.y, min.z}, {max.x, max.y, min.z}, {max.x, max.y, max.z}, {max.x, min.y, max.z}},
                // -X
                {{min.x, min.y, max.z}, {min.x, max.y, max.z}, {min.x, max.y, min.z}, {min.x, min.y, min.z}},
                // +Y
                {{min.x, max.y, min.z}, {min.x, max.y, max.z}, {max.x, max.y, max.z}, {max.x, max.y, min.z}},
                // -Y
                {{min.x, min.y, max.z}, {min.x, min.y, min.z}, {max.x, min.y, min.z}, {max.x, min.y, max.z}},
                // +Z
                {{min.x, min.y, max.z}, {max.x, min.y, max.z}, {max.x, max.y, max.z}, {min.x, max.y, max.z}},
                // -Z
                {{max.x, min.y, min.z}, {min.x, min.y, min.z}, {min.x, max.y, min.z}, {max.x, max.y, min.z}},
        };
        float[][] uvs = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};

        int vi = 0;
        int ii = 0;
        for (int face = 0; face < 6; face++) {
            for (int corner = 0; corner < 4; corner++) {
                long o = (long) vi * stride;
                verts.set(JAVA_FLOAT_UNALIGNED, o, corners[face][corner][0]);
                verts.set(JAVA_FLOAT_UNALIGNED, o + 4, corners[face][corner][1]);
                verts.set(JAVA_FLOAT_UNALIGNED, o + 8, corners[face][corner][2]);
                verts.set(JAVA_FLOAT_UNALIGNED, o + 12, normals[face][0]);
                verts.set(JAVA_FLOAT_UNALIGNED, o + 16, normals[face][1]);
                verts.set(JAVA_FLOAT_UNALIGNED, o + 20, normals[face][2]);
                verts.set(JAVA_FLOAT_UNALIGNED, o + 24, uvs[corner][0]);
                verts.set(JAVA_FLOAT_UNALIGNED, o + 28, uvs[corner][1]);
                vi++;
            }
            int base = face * 4;
            idxs.set(JAVA_SHORT_UNALIGNED, (long) ii * 2, (short) base);
            idxs.set(JAVA_SHORT_UNALIGNED, (long) (ii + 1) * 2, (short) (base + 1));
            idxs.set(JAVA_SHORT_UNALIGNED, (long) (ii + 2) * 2, (short) (base + 2));
            idxs.set(JAVA_SHORT_UNALIGNED, (long) (ii + 3) * 2, (short) base);
            idxs.set(JAVA_SHORT_UNALIGNED, (long) (ii + 4) * 2, (short) (base + 2));
            idxs.set(JAVA_SHORT_UNALIGNED, (long) (ii + 5) * 2, (short) (base + 3));
            ii += 6;
        }
    }
}
