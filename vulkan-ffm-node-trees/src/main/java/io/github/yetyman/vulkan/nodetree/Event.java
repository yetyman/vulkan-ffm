package io.github.yetyman.vulkan.nodetree;

/**
 * Base class for all events dispatched through the node node tree.
 *
 * Events propagate through two phases:
 *   CAPTURE: root -> target (ancestors get first look)
 *   BUBBLE: target -> root (target and ancestors react)
 *
 * Each event carries its {@link EventType} token, which is used by the typed handler
 * registration system to dispatch only to interested handlers.
 *
 * The base system is not spatial - it only provides propagation through the tree
 * structure given a caller-chosen origin node.
 */
public abstract class Event {

    private final EventType<?> eventType;
    private Phase phase = Phase.CAPTURE;
    private boolean stopped = false;

    /**
     * Constructs an event with its type token.
     *
     * @param eventType the type token identifying this event's kind
     */
    protected Event(EventType<?> eventType) {
        this.eventType = eventType;
    }

    /** @return the type token for this event. */
    public EventType<?> eventType() { return eventType; }

    /** @return the current dispatch phase. */
    public Phase phase() { return phase; }

    /**
     * Sets the active dispatch phase. Package-private - called by dispatch machinery.
     */
    void setPhase(Phase phase) { this.phase = phase; }

    /**
     * Prevents further propagation of this event in the current phase.
     * Stopped resets between capture and bubble (follows existing PropagationState precedent).
     */
    public void stopPropagation() { this.stopped = true; }

    /** @return true if propagation has been stopped. */
    public boolean isStopped() { return stopped; }

    /**
     * Resets the stopped flag for the bubble phase transition.
     */
    void resetForBubble() { this.stopped = false; }

    /** Dispatch phases. */
    public enum Phase {
        CAPTURE, BUBBLE
    }
}
