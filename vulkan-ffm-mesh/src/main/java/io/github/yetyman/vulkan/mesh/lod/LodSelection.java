package io.github.yetyman.vulkan.mesh.lod;

import io.github.yetyman.vulkan.mesh.consume.GeometryDrawRange;
import io.github.yetyman.vulkan.mesh.residency.PartitionRef;

import java.util.List;
import java.util.Map;

/**
 * The output of an {@link LodSelector}: what to render and what residency to request.
 *
 * <p>There are exactly four output shapes, mirroring the three decision locations
 * (CPU-explicit, GPU-indirect, hardware-implicit) plus a "nothing" case:
 *
 * <ul>
 *   <li>{@link Explicit} - CPU selected concrete draw ranges. The most common case for
 *       discrete LOD, distance-band selection, and any CPU-driven system.</li>
 *   <li>{@link Indirect} - the GPU will produce the selection; this carries the dispatch
 *       description and the output buffers. Used for Nanite-style cluster LOD selection.</li>
 *   <li>{@link Parametric} - continuous parameters the hardware or shader uses to control
 *       detail. Used for tessellation, displacement, mesh-shader amplification.</li>
 *   <li>{@link None} - nothing to render (too far, budget exhausted, not yet resident).
 *       May carry residency requests so the system can begin loading for next frame.</li>
 * </ul>
 *
 * <p>All variants may carry {@link #residencyRequests()}: partitions that should be loaded
 * but are not yet resident. This is normal streaming behavior, not an error. A selector
 * saying "render LOD 2 but please start loading LOD 1" is the expected steady state during
 * camera motion.
 *
 * <p>All variants may carry a {@link TransitionState} indicating that a transition between
 * representations is in progress (cross-fade, geomorph, dither). The renderer reads this to
 * know whether to blend between two representations and at what factor.
 */
public sealed interface LodSelection
        permits LodSelection.Explicit,
                LodSelection.Indirect,
                LodSelection.Parametric,
                LodSelection.None {

    /**
     * @return partition refs the system should begin loading but which are not required this
     * frame. Empty when no prefetching is needed.
     */
    List<PartitionRef> residencyRequests();

    /**
     * @return the active transition state, or null if no transition is in progress (hard cut
     * or no change since last frame)
     */
    TransitionState transition();

    /**
     * @return the node index within the {@link RepresentationStructure} that was selected,
     * or -1 when not applicable (Indirect where the GPU decides, or None)
     */
    int selectedNodeIndex();

    // -------------------------------------------------------------------------
    // Variants
    // -------------------------------------------------------------------------

    /**
     * CPU-explicit: concrete ranges to draw this frame. The consumer issues
     * {@code vkCmdDrawIndexed} (or writes into an indirect buffer) from these.
     *
     * @param ranges            the draw ranges for this frame
     * @param selectedNodeIndex which node in the representation was chosen
     * @param residencyRequests partitions to prefetch
     * @param transition        active transition, or null
     */
    record Explicit(
            List<GeometryDrawRange> ranges,
            int selectedNodeIndex,
            List<PartitionRef> residencyRequests,
            TransitionState transition
    ) implements LodSelection {
        public Explicit {
            if (ranges == null) throw new IllegalArgumentException("ranges required");
            if (residencyRequests == null) residencyRequests = List.of();
        }

        /**
         * Convenience factory for the common case: one selected level, no transition, no prefetch.
         */
        public static Explicit of(List<GeometryDrawRange> ranges, int nodeIndex) {
            return new Explicit(ranges, nodeIndex, List.of(), null);
        }
    }

    /**
     * GPU-indirect: a compute dispatch (or multi-pass dispatch) that will produce the selection.
     * The dispatch reads the {@link io.github.yetyman.vulkan.mesh.consume.GeometryTable} and writes
     * indirect draw arguments into output buffers.
     *
     * <p>The consumer records the dispatch via {@link DispatchDescription#record}, then issues
     * {@code vkCmdDrawIndexedIndirectCount} against the output buffers. No CPU per-mesh work
     * occurs in the render path.
     *
     * @param dispatch          describes the GPU work to record and its output buffers
     * @param selectedNodeIndex -1 (the GPU decides which nodes to draw)
     * @param residencyRequests partitions to prefetch (may be driven by feedback from last frame)
     * @param transition        active transition, or null
     */
    record Indirect(
            DispatchDescription dispatch,
            int selectedNodeIndex,
            List<PartitionRef> residencyRequests,
            TransitionState transition
    ) implements LodSelection {
        public Indirect {
            if (dispatch == null) throw new IllegalArgumentException("dispatch required");
            if (residencyRequests == null) residencyRequests = List.of();
        }

        /**
         * Convenience factory: GPU dispatch, no prefetch, no transition.
         */
        public static Indirect of(DispatchDescription dispatch) {
            return new Indirect(dispatch, -1, List.of(), null);
        }
    }

    /**
     * Hardware-implicit: continuous parameters that the fixed-function stage or a shader
     * interprets. The consumer writes them to push constants, specialization constants, or
     * uniform buffers depending on the pipeline design.
     *
     * @param parameters        named parameter values for this frame
     * @param selectedNodeIndex 0 (parametric always has one base node)
     * @param residencyRequests partitions to prefetch
     * @param transition        active transition, or null
     */
    record Parametric(
            Map<String, Float> parameters,
            int selectedNodeIndex,
            List<PartitionRef> residencyRequests,
            TransitionState transition
    ) implements LodSelection {
        public Parametric {
            if (parameters == null || parameters.isEmpty())
                throw new IllegalArgumentException("at least one parameter required");
            if (residencyRequests == null) residencyRequests = List.of();
        }

        /**
         * Convenience factory: parameters only, no prefetch, no transition.
         */
        public static Parametric of(Map<String, Float> parameters) {
            return new Parametric(parameters, 0, List.of(), null);
        }
    }

    /**
     * Nothing to render this frame. The geometry is too far, budget-exhausted, or not yet
     * resident. May carry residency requests so loading can begin for next frame.
     *
     * @param residencyRequests partitions to start loading
     * @param transition        null (no transition when nothing is drawn)
     */
    record None(
            List<PartitionRef> residencyRequests,
            TransitionState transition
    ) implements LodSelection {
        public None {
            if (residencyRequests == null) residencyRequests = List.of();
        }

        /**
         * @return always -1
         */
        @Override
        public int selectedNodeIndex() { return -1; }

        /**
         * Convenience: nothing to draw, nothing to load.
         */
        public static final None EMPTY = new None(List.of(), null);

        /**
         * Convenience: nothing to draw, but request these partitions for next frame.
         */
        public static None withRequests(List<PartitionRef> requests) {
            return new None(requests, null);
        }
    }
}
