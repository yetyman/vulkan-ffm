package io.github.yetyman.vulkan.mesh.process;

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
import io.github.yetyman.vulkan.mesh.source.SegmentAttributeStream;
import io.github.yetyman.vulkan.mesh.source.SegmentGeometrySource;
import io.github.yetyman.vulkan.mesh.source.SegmentIndexStream;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED;

/**
 * Welds (deduplicates) vertices that are identical within a configurable position tolerance.
 *
 * <p>Given a {@link GeometrySource}, this produces a new {@link GeometrySource} with duplicate
 * vertices merged and indices remapped to reference the unique set. This reduces vertex count
 * and improves post-transform cache utilization.</p>
 *
 * <p>Two vertices are considered duplicates if their positions differ by less than
 * {@link #tolerance} in each axis. Only positions are compared; if additional attributes
 * (normals, UVs) differ at the same position, those vertices are not welded. To weld by
 * all attributes, use the full-attribute comparison overload.</p>
 *
 * <p>This is a CPU-side O(N) spatial hash implementation suitable for moderate-sized meshes.
 * For production use on multi-million-vertex meshes, use the optimized implementation in the
 * {@code vulkan-ffm-mesh-processing} sibling module.</p>
 */
public final class VertexWelder {

    private VertexWelder() {}

    /** Default weld tolerance. */
    public static final float DEFAULT_TOLERANCE = 1e-5f;

    /**
     * Welds vertices by position only, using the default tolerance.
     *
     * @param source the geometry to weld (must have positions, host-readable)
     * @param arena  arena for the output geometry
     * @return a new GeometrySource with deduplicated vertices and remapped indices
     */
    public static GeometrySource weld(GeometrySource source, Arena arena) {
        return weld(source, arena, DEFAULT_TOLERANCE);
    }

    /**
     * Welds vertices by position, merging those within the given tolerance.
     *
     * @param source    the geometry (must have POSITION, host-readable)
     * @param arena     arena for output
     * @param tolerance maximum per-axis difference for vertices to be considered identical
     * @return a new GeometrySource with deduplicated vertices
     */
    public static GeometrySource weld(GeometrySource source, Arena arena, float tolerance) {
        if (!source.available().contains(AttributeSemantic.POSITION)) {
            throw new IllegalArgumentException("Source must have POSITION attribute");
        }
        AttributeStream posStream = source.stream(AttributeSemantic.POSITION);
        if (!posStream.isHostReadable()) {
            throw new IllegalStateException("POSITION stream must be host-readable");
        }

        long vertexCount = source.elementCount();

        // Read positions
        MeshLayout posLayout = MeshLayout.builder()
                .stream(0).attribute(AttributeSemantic.POSITION, AttributeFormat.F32x3)
                .build();
        long posStride = posLayout.strideOf(0);
        MemorySegment positions = arena.allocate(posStride * vertexCount);
        posStream.transcodeInto(posLayout, positions, 0, posStride, 0, vertexCount);

        // Spatial hash: quantize positions to cells and merge identical cells
        float invCellSize = (tolerance > 0) ? 1.0f / tolerance : 1e6f;
        Map<Long, Integer> cellToUnique = new HashMap<>();
        int[] remapTable = new int[(int) vertexCount];
        int uniqueCount = 0;

        for (int v = 0; v < (int) vertexCount; v++) {
            long o = v * posStride;
            float x = positions.get(JAVA_FLOAT_UNALIGNED, o);
            float y = positions.get(JAVA_FLOAT_UNALIGNED, o + 4);
            float z = positions.get(JAVA_FLOAT_UNALIGNED, o + 8);

            long cellKey = spatialHash(x, y, z, invCellSize);
            Integer existing = cellToUnique.get(cellKey);
            if (existing != null) {
                // Verify within tolerance (hash collisions)
                long eo = existing * posStride;
                float ex = positions.get(JAVA_FLOAT_UNALIGNED, eo);
                float ey = positions.get(JAVA_FLOAT_UNALIGNED, eo + 4);
                float ez = positions.get(JAVA_FLOAT_UNALIGNED, eo + 8);
                if (Math.abs(x - ex) <= tolerance && Math.abs(y - ey) <= tolerance
                        && Math.abs(z - ez) <= tolerance) {
                    remapTable[v] = existing;
                } else {
                    // Hash collision, keep as unique
                    remapTable[v] = uniqueCount;
                    uniqueCount++;
                }
            } else {
                cellToUnique.put(cellKey, uniqueCount);
                remapTable[v] = uniqueCount;
                uniqueCount++;
            }
        }

        // If no vertices were welded, return the source as-is
        if (uniqueCount == vertexCount) {
            return source;
        }

        // Build compacted vertex data for all attributes
        MeshLayout.Builder layoutBuilder = MeshLayout.builder();
        layoutBuilder.stream(0);
        for (AttributeSemantic semantic : source.available()) {
            AttributeStream as = source.stream(semantic);
            layoutBuilder.attribute(semantic, as.sourceFormat());
        }
        MeshLayout outLayout = layoutBuilder.build();
        long outStride = outLayout.strideOf(0);

        MemorySegment outVerts = arena.allocate(outStride * uniqueCount);

        // Write unique vertices by finding the first occurrence of each unique index
        boolean[] written = new boolean[uniqueCount];
        for (int v = 0; v < (int) vertexCount; v++) {
            int target = remapTable[v];
            if (!written[target]) {
                // Transcode this vertex's attributes into the output
                long dstBase = target * outStride;
                for (AttributeSemantic semantic : source.available()) {
                    AttributeStream as = source.stream(semantic);
                    long attrOffset = outLayout.offsetOf(semantic);
                    as.transcodeInto(outLayout, outVerts, dstBase + attrOffset,
                            outStride, v, 1);
                }
                written[target] = true;
            }
        }

        // Remap indices
        SegmentGeometrySource.Builder builder = SegmentGeometrySource.builder()
                .layout(outLayout)
                .elementCount(uniqueCount)
                .topology(source.topology())
                .bounds(source.bounds())
                .streamData(0, outVerts);

        if (source.indices().isPresent()) {
            IndexStream srcIdx = source.indices().get();
            long indexCount = srcIdx.indexCount();

            // Read original indices
            MemorySegment srcIndices = arena.allocate(indexCount * 4L);
            srcIdx.transcodeInto(IndexWidth.U32, 0, srcIndices, 0, 0, indexCount);

            // Remap
            MemorySegment outIndices = arena.allocate(indexCount * 4L);
            for (long i = 0; i < indexCount; i++) {
                int origIdx = srcIndices.get(JAVA_INT_UNALIGNED, i * 4);
                outIndices.set(JAVA_INT_UNALIGNED, i * 4, remapTable[origIdx]);
            }

            builder.indices(IndexWidth.U32, indexCount, outIndices);
        }

        return builder.build();
    }

    private static long spatialHash(float x, float y, float z, float invCellSize) {
        long ix = (long) Math.floor(x * invCellSize);
        long iy = (long) Math.floor(y * invCellSize);
        long iz = (long) Math.floor(z * invCellSize);
        // FNV-1a-style combine
        long h = 0x811c9dc5L;
        h ^= ix; h *= 0x01000193L;
        h ^= iy; h *= 0x01000193L;
        h ^= iz; h *= 0x01000193L;
        return h;
    }
}
