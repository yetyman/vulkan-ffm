package io.github.yetyman.vulkan.mesh.lod;

import io.github.yetyman.vulkan.mesh.partition.FloatChannelKey;
import io.github.yetyman.vulkan.mesh.partition.IntChannelKey;

/**
 * Well-known metadata channel keys for LOD systems. These are the channels that LOD selectors
 * and GPU-driven culling/selection shaders commonly need.
 *
 * <p>All keys are {@code static final} identity tokens. The actual per-partition data lives in
 * {@link io.github.yetyman.vulkan.mesh.partition.PartitionMetadata}, one registry per partition set.
 * Two subsystems using the same key on the same metadata registry are guaranteed to read/write
 * the same backing array.
 *
 * <p>Usage:
 * <pre>{@code
 * // At load time: write error bounds into the metadata registry
 * PartitionMetadata metadata = partitionSet.metadata();
 * float[] errors = metadata.floatChannel(LodChannels.ERROR_BOUND);
 * for (int i = 0; i < count; i++) errors[i] = computedError[i];
 *
 * // At selection time: read directly from the array
 * float[] errors = metadata.floatChannel(LodChannels.ERROR_BOUND);
 * float error = errors[partitionIndex]; // O(1), no boxing, no lookup
 *
 * // For GPU upload:
 * MetadataStore store = metadata.floatStore(LodChannels.ERROR_BOUND);
 * store.bulkWriteTo(gpuSegment, offset, 0, count); // single memcpy
 * }</pre>
 */
public final class LodChannels {

    private LodChannels() {}

    // -------------------------------------------------------------------------
    // Error metrics
    // -------------------------------------------------------------------------

    /**
     * Per-partition geometric error bound in world-space units. The maximum distance any vertex
     * in this partition deviates from the full-detail original. Used by screen-space-error
     * selectors to project to pixels and decide visibility.
     */
    public static final FloatChannelKey ERROR_BOUND = FloatChannelKey.of("lodErrorBound");

    /**
     * Per-partition parent error bound: the error of the coarser representation this partition
     * is a refinement of. Used together with {@link #ERROR_BOUND} for DAG traversal:
     * a cluster is drawn when its own error is acceptable but its parent's error is not.
     */
    public static final FloatChannelKey PARENT_ERROR = FloatChannelKey.of("lodParentError");

    // -------------------------------------------------------------------------
    // DAG connectivity (for GPU-driven DAG traversal)
    // -------------------------------------------------------------------------

    /**
     * Per-partition node index in a {@link RepresentationGraph}. Maps a partition back to the
     * LOD node it belongs to, so a GPU shader can look up per-node error bounds from a
     * separate node-data SSBO.
     */
    public static final IntChannelKey NODE_INDEX = IntChannelKey.of("lodNodeIndex");

    /**
     * Per-partition LOD level (for flat/chain structures). 0 = finest, higher = coarser.
     * Used by CPU selectors for fast level lookup and by GPU shaders for debug visualization.
     */
    public static final IntChannelKey LOD_LEVEL = IntChannelKey.of("lodLevel");

    // -------------------------------------------------------------------------
    // Group identity (for cluster-based LOD)
    // -------------------------------------------------------------------------

    /**
     * Per-partition group ID within a DAG level. Clusters that were simplified together share
     * a group ID. Used by GPU selection to ensure all clusters in a group are selected or
     * rejected together (monotonic cut property).
     */
    public static final IntChannelKey GROUP_ID = IntChannelKey.of("lodGroupId");
}
