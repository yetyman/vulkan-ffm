package io.github.yetyman.vulkan.mesh.lod;

/**
 * Selects which representation(s) to render for a given geometry this frame.
 *
 * <p>A selector is a <b>stateful instance</b>, not a static method. It holds:
 * <ul>
 *   <li>Policy configuration (error thresholds, distance bands, budget references)</li>
 *   <li>Hysteresis state (preventing oscillation when near a threshold boundary)</li>
 *   <li>Per-instance tracking for transitions in progress</li>
 * </ul>
 *
 * <p>There will be many selector instances with different configurations in one application.
 * A terrain system, a character system, and a prop system may each use a different selector
 * even within the same frame.
 *
 * <h2>Decision location</h2>
 * <p>The selector interface is agnostic to where the decision happens:
 * <ul>
 *   <li>A CPU selector traverses the structure and returns {@link LodSelection.Explicit}</li>
 *   <li>A GPU selector returns {@link LodSelection.Indirect} with a compute dispatch that will
 *       produce the selection on the GPU</li>
 *   <li>A hardware-implicit selector returns {@link LodSelection.Parametric} with values the
 *       fixed-function pipeline uses</li>
 * </ul>
 *
 * <p>The consumer of {@link LodSelection} does not know or care which path produced it, because
 * all three output shapes can be rendered without further selection logic.
 *
 * <h2>Residency awareness</h2>
 * <p>A selector should consult {@link LodContext#residencyOf} and may return a degraded selection
 * plus residency requests rather than selecting something that is not yet resident. This mirrors
 * the degradation concept in the frame graph without depending on it.
 *
 * @see LodSelection
 * @see LodContext
 */
public interface LodSelector {

    /**
     * Selects which representation(s) to render given the current context.
     *
     * <p>The returned selection is valid for this frame only. The selector may return different
     * results on subsequent calls even with the same context, due to hysteresis, transitions,
     * or async residency changes.
     *
     * @param representations the structural data describing available LOD options
     * @param context         per-frame context: camera, budgets, residency, feedback
     * @return the selection result; never null
     */
    LodSelection select(RepresentationStructure representations, LodContext context);

    /**
     * Notifies the selector that a frame has completed. Selectors with time-based hysteresis
     * or transition tracking should advance their internal state.
     *
     * <p>Default implementation is a no-op for selectors with no temporal state.
     *
     * @param deltaTimeSeconds time elapsed since the last frame, in seconds
     */
    default void frameAdvance(float deltaTimeSeconds) {}

    /**
     * Resets all hysteresis and transition state. Called when the camera teleports, the scene
     * is rebuilt, or the selector is being repurposed for a different geometry.
     */
    default void reset() {}
}
