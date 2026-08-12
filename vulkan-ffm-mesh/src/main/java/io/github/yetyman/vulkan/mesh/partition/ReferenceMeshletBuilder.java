package io.github.yetyman.vulkan.mesh.partition;

import io.github.yetyman.helpers.math.Vec3;
import io.github.yetyman.helpers.math.geometry.AABB;
import io.github.yetyman.vulkan.mesh.AttributeFormat;
import io.github.yetyman.vulkan.mesh.AttributeSemantic;
import io.github.yetyman.vulkan.mesh.IndexWidth;
import io.github.yetyman.vulkan.mesh.MeshLayout;
import io.github.yetyman.vulkan.mesh.PrimitiveTopology;
import io.github.yetyman.vulkan.mesh.source.GeometrySource;
import io.github.yetyman.vulkan.mesh.source.IndexStream;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED;

/**
 * A naive, sequential meshlet builder intended only for testing and as a reference implementation.
 * Not suitable for production use; optimized meshlet builders that maximize vertex reuse and
 * minimize overdraw belong in the {@code vulkan-ffm-mesh-processing} sibling module.
 *
 * <p>This builder partitions an indexed triangle-list geometry into meshlets of at most
 * {@link #maxVertices} unique vertices and {@link #maxPrimitives} triangles each, processing
 * triangles in index-buffer order with no reordering or spatial optimization.</p>
 *
 * <p>The resulting meshlets are returned as a {@link PartitionSet} where each partition covers
 * a contiguous range of the index stream and has computed bounds from the referenced vertices.</p>
 *
 * <p><b>Limitations (by design -- this is a reference, not a production implementation):</b></p>
 * <ul>
 *   <li>Sequential scan of the index buffer; no vertex-cache optimization</li>
 *   <li>No spatial locality heuristic; meshlets do not minimize screen-space overdraw</li>
 *   <li>No cone culling metadata generation (that belongs in the optimized builder)</li>
 *   <li>Only triangle lists supported</li>
 * </ul>
 */
public final class ReferenceMeshletBuilder implements PartitioningStrategy {

    /** Default maximum vertices per meshlet (NV_mesh_shader recommendation). */
    public static final int DEFAULT_MAX_VERTICES = 64;
    /** Default maximum primitives per meshlet (NV_mesh_shader recommendation). */
    public static final int DEFAULT_MAX_PRIMITIVES = 126;

    private final int maxVertices;
    private final int maxPrimitives;

    /**
     * @param maxVertices   maximum unique vertices per meshlet (typically 64)
     * @param maxPrimitives maximum triangles per meshlet (typically 124 or 126)
     */
    public ReferenceMeshletBuilder(int maxVertices, int maxPrimitives) {
        if (maxVertices < 3) throw new IllegalArgumentException("maxVertices must be >= 3");
        if (maxPrimitives < 1) throw new IllegalArgumentException("maxPrimitives must be >= 1");
        this.maxVertices = maxVertices;
        this.maxPrimitives = maxPrimitives;
    }

    /**
     * Creates a reference meshlet builder with default limits (64 vertices, 126 primitives).
     */
    public ReferenceMeshletBuilder() {
        this(DEFAULT_MAX_VERTICES, DEFAULT_MAX_PRIMITIVES);
    }

    @Override
    public PartitionSet partition(GeometrySource source) {
        if (source.topology() != PrimitiveTopology.TRIANGLE_LIST) {
            throw new IllegalArgumentException(
                    "ReferenceMeshletBuilder requires TRIANGLE_LIST topology, got: "
                    + source.topology().name());
        }
        if (source.indices().isEmpty()) {
            throw new IllegalArgumentException(
                    "ReferenceMeshletBuilder requires indexed geometry");
        }
        IndexStream idxStream = source.indices().get();
        if (!idxStream.isHostReadable()) {
            throw new IllegalStateException(
                    "ReferenceMeshletBuilder requires host-readable indices");
        }

        long indexCount = idxStream.indexCount();
        long triangleCount = indexCount / 3;

        // Read all indices and positions into temp memory for processing
        try (Arena arena = Arena.ofConfined()) {
            // Read indices as U32
            MemorySegment indices = arena.allocate(indexCount * 4L);
            idxStream.transcodeInto(IndexWidth.U32, 0, indices, 0, 0, indexCount);

            // Read positions for bounds computation
            MemorySegment positions = null;
            long posStride = 0;
            if (source.available().contains(AttributeSemantic.POSITION)
                    && source.stream(AttributeSemantic.POSITION).isHostReadable()) {
                MeshLayout posLayout = MeshLayout.builder()
                        .stream(0).attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3)
                        .build();
                posStride = posLayout.strideOf(0);
                positions = arena.allocate(posStride * source.elementCount());
                source.stream(AttributeSemantic.POSITION).transcodeInto(
                        posLayout, positions, 0, posStride, 0, source.elementCount());
            }

            return buildMeshlets(indices, triangleCount, positions, posStride, source.bounds());
        }
    }

    private PartitionSet buildMeshlets(MemorySegment indices, long triangleCount,
                                       MemorySegment positions, long posStride,
                                       AABB sourceBounds) {
        List<GeometryPartition> meshlets = new ArrayList<>();

        // Current meshlet state
        // Track unique vertices with a simple array scan (naive but correct for reference impl)
        int[] meshletVertices = new int[maxVertices];
        int meshletVertexCount = 0;
        int meshletPrimitiveCount = 0;
        long meshletFirstIndex = 0;

        for (long tri = 0; tri < triangleCount; tri++) {
            long triBase = tri * 3;
            int v0 = indices.get(JAVA_INT_UNALIGNED, triBase * 4);
            int v1 = indices.get(JAVA_INT_UNALIGNED, (triBase + 1) * 4);
            int v2 = indices.get(JAVA_INT_UNALIGNED, (triBase + 2) * 4);

            // Count how many new vertices this triangle would add
            int newVerts = 0;
            if (!containsVertex(meshletVertices, meshletVertexCount, v0)) newVerts++;
            if (!containsVertex(meshletVertices, meshletVertexCount, v1)) newVerts++;
            if (!containsVertex(meshletVertices, meshletVertexCount, v2)) newVerts++;

            // Check if this triangle fits in the current meshlet
            boolean fits = (meshletVertexCount + newVerts <= maxVertices)
                        && (meshletPrimitiveCount + 1 <= maxPrimitives);

            if (!fits && meshletPrimitiveCount > 0) {
                // Emit current meshlet
                meshlets.add(createMeshletPartition(
                        meshlets.size(), meshletFirstIndex, meshletPrimitiveCount,
                        meshletVertices, meshletVertexCount, positions, posStride, sourceBounds));

                // Start new meshlet
                meshletVertexCount = 0;
                meshletPrimitiveCount = 0;
                meshletFirstIndex = triBase;

                // Re-check: the triangle must fit in a fresh meshlet
                newVerts = 3; // all vertices are new in a fresh meshlet
            }

            // Add vertices
            if (!containsVertex(meshletVertices, meshletVertexCount, v0)) {
                meshletVertices[meshletVertexCount++] = v0;
            }
            if (!containsVertex(meshletVertices, meshletVertexCount, v1)) {
                meshletVertices[meshletVertexCount++] = v1;
            }
            if (!containsVertex(meshletVertices, meshletVertexCount, v2)) {
                meshletVertices[meshletVertexCount++] = v2;
            }
            meshletPrimitiveCount++;
        }

        // Emit final meshlet
        if (meshletPrimitiveCount > 0) {
            meshlets.add(createMeshletPartition(
                    meshlets.size(), meshletFirstIndex, meshletPrimitiveCount,
                    meshletVertices, meshletVertexCount, positions, posStride, sourceBounds));
        }

        return PartitionSet.of(meshlets);
    }

    private GeometryPartition createMeshletPartition(
            int meshletIndex, long firstIndex, int primitiveCount,
            int[] vertices, int vertexCount,
            MemorySegment positions, long posStride, AABB fallbackBounds) {

        AABB bounds;
        if (positions != null) {
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
            for (int i = 0; i < vertexCount; i++) {
                long base = vertices[i] * posStride;
                float px = positions.get(JAVA_FLOAT_UNALIGNED, base);
                float py = positions.get(JAVA_FLOAT_UNALIGNED, base + 4);
                float pz = positions.get(JAVA_FLOAT_UNALIGNED, base + 8);
                minX = Math.min(minX, px); minY = Math.min(minY, py); minZ = Math.min(minZ, pz);
                maxX = Math.max(maxX, px); maxY = Math.max(maxY, py); maxZ = Math.max(maxZ, pz);
            }
            bounds = new AABB(new Vec3(minX, minY, minZ), new Vec3(maxX, maxY, maxZ));
        } else {
            bounds = fallbackBounds;
        }

        return new GeometryPartition(
                "meshlet_" + meshletIndex,
                firstIndex,
                primitiveCount,
                vertexCount,
                PrimitiveTopology.TRIANGLE_LIST,
                bounds,
                0,   // tag: all meshlets share the same tag by default
                meshletIndex  // sortKey: meshlet order
        );
    }

    private static boolean containsVertex(int[] vertices, int count, int vertex) {
        for (int i = 0; i < count; i++) {
            if (vertices[i] == vertex) return true;
        }
        return false;
    }

    /**
     * @return the maximum vertices per meshlet this builder was configured with
     */
    public int maxVertices() {
        return maxVertices;
    }

    /**
     * @return the maximum primitives per meshlet this builder was configured with
     */
    public int maxPrimitives() {
        return maxPrimitives;
    }
}
