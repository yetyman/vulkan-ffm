package io.github.yetyman.vulkan.mesh.lod;

/**
 * Tracks the state of an in-progress transition between two LOD representations for one
 * mesh instance.
 *
 * <p>Mutable: the selector advances it each frame via {@link #advance(float)}. The renderer
 * reads it to determine blending factor, which representations to draw, and when the
 * transition completes.
 *
 * <p>This is per-instance state. Each mesh instance that is transitioning has its own
 * {@code TransitionState}. Instances not transitioning have null in their {@link LodSelection}.
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>A selector detects a level change and creates a new TransitionState</li>
 *   <li>Each frame, the selector calls {@link #advance(float)} with deltaTime</li>
 *   <li>The renderer reads {@link #factor()}, {@link #fromNodeIndex()}, {@link #toNodeIndex()}</li>
 *   <li>When {@link #isComplete()}, the selector drops the state and the new level becomes current</li>
 * </ol>
 */
public final class TransitionState {

    private final TransitionMode mode;
    private final int fromNodeIndex;
    private final int toNodeIndex;
    private final float durationSeconds;
    private float elapsed;

    /**
     * Creates a new transition.
     *
     * @param mode          how the transition should be rendered
     * @param fromNodeIndex the representation node being transitioned away from
     * @param toNodeIndex   the representation node being transitioned to
     */
    public TransitionState(TransitionMode mode, int fromNodeIndex, int toNodeIndex) {
        this.mode = mode;
        this.fromNodeIndex = fromNodeIndex;
        this.toNodeIndex = toNodeIndex;
        this.durationSeconds = computeDuration(mode);
        this.elapsed = 0.0f;
    }

    private static float computeDuration(TransitionMode mode) {
        return switch (mode) {
            case TransitionMode.HardCut ignored -> 0.0f;
            case TransitionMode.Dither d -> d.durationSeconds();
            case TransitionMode.Geomorph g -> g.durationSeconds();
            case TransitionMode.CrossFade c -> c.durationSeconds();
            case TransitionMode.Continuous ignored -> 0.0f;
        };
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** The transition mode dictating how the renderer should blend. */
    public TransitionMode mode() { return mode; }

    /** The node index being transitioned away from. */
    public int fromNodeIndex() { return fromNodeIndex; }

    /** The node index being transitioned to. */
    public int toNodeIndex() { return toNodeIndex; }

    /**
     * Blend factor in [0, 1]. 0 = fully showing "from", 1 = fully showing "to".
     * For {@link TransitionMode.HardCut} and {@link TransitionMode.Continuous}, always 1.
     */
    public float factor() {
        if (durationSeconds <= 0.0f) return 1.0f;
        return Math.min(1.0f, elapsed / durationSeconds);
    }

    /**
     * @return true when the transition has completed (factor >= 1.0). The selector should
     * drop this state and commit to the "to" node.
     */
    public boolean isComplete() {
        return durationSeconds <= 0.0f || elapsed >= durationSeconds;
    }

    /**
     * @return time remaining in seconds, or 0 if complete
     */
    public float remainingSeconds() {
        return Math.max(0.0f, durationSeconds - elapsed);
    }

    /**
     * @return total duration of this transition in seconds
     */
    public float durationSeconds() { return durationSeconds; }

    /**
     * @return elapsed time since transition began
     */
    public float elapsedSeconds() { return elapsed; }

    // -------------------------------------------------------------------------
    // Mutation
    // -------------------------------------------------------------------------

    /**
     * Advances the transition by deltaTime seconds. Called once per frame by the selector.
     *
     * @param deltaTimeSeconds time elapsed since last frame
     */
    public void advance(float deltaTimeSeconds) {
        this.elapsed += deltaTimeSeconds;
    }

    /**
     * Forces the transition to complete immediately (e.g. on camera teleport).
     */
    public void complete() {
        this.elapsed = durationSeconds;
    }
}
