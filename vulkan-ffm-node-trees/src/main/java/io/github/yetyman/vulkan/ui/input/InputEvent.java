package io.github.yetyman.vulkan.ui.input;

import io.github.yetyman.vulkan.ui.input.types.InputEventData;

/**
 * Input event passed through the capture/bubble dual-pass dispatch.
 *
 * Contains the event type and phase, a PropagationState with stop/context/handled tracking,
 * and a composition-based payload via InputEventData subclasses. Only the data object relevant
 * to type() is present; access it via {@link #data()} or the typed accessor {@link #data(Class)}.
 *
 * Event construction is owned by the InputEventData subclasses themselves.
 *
 * Layers inspect phase() to determine whether they are in capture or bubble.
 * Layers call the propagation convenience methods to control flow.
 */
public class InputEvent {

    private final InputEventType type;
    private final InputEventData data;
    private final PropagationState propagation;
    private final long timestampNanos;
    private InputPhase phase;

    public InputEvent(InputEventType type, InputEventData data) {
        this.type = type;
        this.data = data;
        this.propagation = new PropagationState();
        this.timestampNanos = System.nanoTime();
    }


    // --- Core accessors ---

    public InputEventType type() { return type; }
    public InputPhase phase() { return phase; }
    public long timestampNanos() { return timestampNanos; }
    public PropagationState propagation() { return propagation; }

    /** @return the raw data payload, or null for events with no payload (focus). */
    public InputEventData data() { return data; }

    /**
     * @return the data payload cast to the expected type.
     * @throws ClassCastException if the data is not of the expected type.
     */
    @SuppressWarnings("unchecked")
    public <T extends InputEventData> T data(Class<T> type) { return (T) data; }

    /** Sets the active dispatch phase. Called by UIInputDispatcher only. */
    void setPhase(InputPhase phase) { this.phase = phase; }

    // --- Convenience propagation methods (delegate to propagation state) ---

    /** Prevents further layers from seeing this event in the current phase. */
    public void stopPropagation() { propagation.stop(); }

    /** Same as stopPropagation(), plus prevents other handlers on the same layer. */
    public void stopImmediatePropagation() { propagation.stopImmediate(); }

    /** Marks the event as handled. Informational only - does not stop propagation. */
    public void markHandled() { propagation.markHandled(); }

    /** @return true if some layer has marked this event as handled. */
    public boolean isHandled() { return propagation.isHandled(); }
}
