package io.github.yetyman.vulkan.foundation.ecs;

/**
 * Base class for all events dispatched through the ECS node tree.
 *
 * Events propagate through two phases:
 *   CAPTURE: root -> target (ancestors get first look)
 *   BUBBLE: target -> root (target and ancestors react)
 *
 * The base system is not spatial - it only provides propagation through the tree
 * structure given a caller-chosen origin node. Hit-testing is the responsibility
 * of higher-level components.
 */
public abstract class Event {

    private Phase phase = Phase.CAPTURE;
    private boolean stopped = false;

    /** @return the current dispatch phase. */
    public Phase phase() { return phase; }

    /**
     * Sets the active dispatch phase. Package-private - called by Node.fireEvent dispatch machinery.
     */
    void setPhase(Phase phase) { this.phase = phase; }

    /**
     * Prevents further propagation of this event in the current phase.
     * Consistent with existing foundation precedent: stopped resets between capture and bubble.
     */
    public void stopPropagation() { this.stopped = true; }

    /** @return true if propagation has been stopped. */
    public boolean isStopped() { return stopped; }

    /**
     * Resets the stopped flag for the bubble phase transition.
     * Follows the existing PropagationState.resetForBubble() precedent in this codebase.
     */
    void resetForBubble() { this.stopped = false; }

    /** Dispatch phases. */
    public enum Phase {
        CAPTURE, BUBBLE
    }
}
