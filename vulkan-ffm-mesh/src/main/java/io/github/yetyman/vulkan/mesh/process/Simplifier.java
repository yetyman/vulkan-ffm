package io.github.yetyman.vulkan.mesh.process;

import io.github.yetyman.vulkan.mesh.source.GeometrySource;

import java.lang.foreign.Arena;

/**
 * Interface for mesh simplification algorithms that reduce triangle count while preserving
 * visual quality within a specified error bound.
 *
 * <p>This interface lives in {@code vulkan-ffm-mesh} so that any module can depend on and
 * implement it. Optimized implementations (QEM, appearance-preserving, GPU-accelerated) belong
 * in the {@code vulkan-ffm-mesh-processing} sibling module or in third-party adapters.</p>
 *
 * <p>The simplification contract is deliberately minimal: given a source and a target ratio,
 * produce a simplified source. Implementations are free to use any algorithm. The error metric
 * and attribute preservation guarantees are implementation-defined and should be documented per
 * implementation.</p>
 */
public interface Simplifier {

    /**
     * Simplifies the geometry to approximately the target ratio of the original triangle count.
     *
     * @param source      the geometry to simplify (must be indexed, triangle-list, host-readable)
     * @param targetRatio ratio of target triangle count to source (0.0 = empty, 1.0 = no change)
     * @param arena       arena for the output geometry
     * @return a simplified geometry source
     * @throws IllegalArgumentException if the source cannot be simplified (wrong topology, etc.)
     */
    GeometrySource simplify(GeometrySource source, float targetRatio, Arena arena);

    /**
     * Simplifies to a target triangle count.
     *
     * @param source              the geometry to simplify
     * @param targetTriangleCount desired number of triangles in the output
     * @param arena               arena for the output
     * @return a simplified geometry source
     */
    default GeometrySource simplifyTo(GeometrySource source, long targetTriangleCount, Arena arena) {
        long currentTriangles = source.indices()
                .map(idx -> idx.indexCount() / 3)
                .orElse(source.elementCount() / 3);
        float ratio = (currentTriangles > 0) ? (float) targetTriangleCount / currentTriangles : 1.0f;
        return simplify(source, Math.max(0, Math.min(1, ratio)), arena);
    }

    /**
     * @return the maximum geometric error introduced by the last simplification, or -1 if
     *         the implementation does not track error bounds.
     */
    default float lastError() {
        return -1.0f;
    }
}
