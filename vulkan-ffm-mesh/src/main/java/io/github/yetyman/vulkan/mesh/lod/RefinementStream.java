package io.github.yetyman.vulkan.mesh.lod;

import io.github.yetyman.vulkan.mesh.source.GeometrySource;

import java.lang.foreign.Arena;

/**
 * An incremental refinement stream that, given a base {@link GeometrySource}, can apply
 * refinement records to produce progressively more detailed geometry.
 *
 * <p>This addresses the progressive mesh case where a mesh is not stored as discrete LOD
 * levels but as a base plus a stream of vertex-split (or edge-collapse-inverse) records. The
 * key property: a base + N records produces a valid mesh at any N, and the error decreases
 * monotonically as N increases.
 *
 * <p>Implementations include:</p>
 * <ul>
 *   <li>Progressive mesh (vertex splits)</li>
 *   <li>Wavelet-based terrain refinement</li>
 *   <li>Streaming point-cloud densification</li>
 *   <li>Subdivision surface refinement</li>
 * </ul>
 *
 * <p>{@link GeometrySource} remains unchanged (fixed element count, random-access streams).
 * The progressive case composes a base source + this stream externally, avoiding the "mutable
 * element count" distortion that would break the source contract.
 *
 * <p>Implementations must be thread-safe for concurrent {@link #refine} calls with different
 * ranges (parallel refinement across multiple instances at different detail levels).
 */
public interface RefinementStream {

    /**
     * @return the total number of refinement records available. Applying all of them to the
     * base yields the maximum-detail representation.
     */
    long recordCount();

    /**
     * @return the geometric error of the base geometry (with zero records applied), relative to
     * the fully-refined mesh. This is the maximum error the stream can reduce to zero.
     */
    float baseError();

    /**
     * @return the approximate geometric error after applying {@code recordCount} records. Must
     * be monotonically non-increasing as recordCount increases. Implementations that cannot
     * compute this cheaply may return a conservative estimate.
     */
    float errorAt(long recordCount);

    /**
     * Applies refinement records [0, upTo) to the base geometry, producing a source at higher
     * detail.
     *
     * <p>The returned source is a new snapshot; it does not alias the base. The caller owns
     * the result and may close the arena to reclaim its memory.
     *
     * <p>Applying zero records returns a source equivalent to the base (though it may be a
     * new copy depending on implementation).
     *
     * @param base  the base geometry this stream refines
     * @param upTo  number of records to apply (0 = base quality, recordCount() = full quality)
     * @param arena arena for the output geometry's memory
     * @return a refined geometry source
     * @throws IllegalArgumentException if upTo is negative or exceeds recordCount()
     */
    GeometrySource refine(GeometrySource base, long upTo, Arena arena);

    /**
     * @return the approximate byte size per refinement record, for budget estimation.
     * Implementations with variable-size records return an average.
     */
    long bytesPerRecord();

    /**
     * @return the total byte size of all refinement records, for residency budgeting
     */
    default long totalBytes() {
        return recordCount() * bytesPerRecord();
    }
}
