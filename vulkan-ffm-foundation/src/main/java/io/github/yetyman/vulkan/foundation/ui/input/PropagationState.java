package io.github.yetyman.vulkan.foundation.ui.input;

import java.util.HashMap;
import java.util.Map;

import static io.github.yetyman.vulkan.foundation.ui.input.InputPhase.CAPTURE;
import static io.github.yetyman.vulkan.foundation.ui.input.InputPhase.PENDING;

/**
 * Tracks propagation state across capture and bubble phases.
 * Carries a context dictionary that layers annotate during capture
 * for downstream layers to read during bubble.
 *
 * Propagation control:
 *   stop()          - prevents further layers from seeing this event in the current phase
 *   stopImmediate()   - same as stop(), plus prevents other handlers on the same layer
 *   markHandled()   - informational flag, does NOT stop propagation
 *
 * Between phases (capture -> bubble), the stopped flag resets but handled and context persist.
 */
public class PropagationState {
    private InputPhase phase;
    private boolean stopped = false;
    private boolean stoppedImmediate = false;
    private boolean handled = false;
    private final Map<String, Object> context = new HashMap<>();

    private final long createNanos;
    private long captureNanos;
    private long bubbleNanos;
    private long completeNanos;

    public PropagationState(){
        this.phase = PENDING;
        this.createNanos = System.nanoTime();
    }

    /** Prevents further layers from seeing this event in the current phase. */
    public void stop() { stopped = true; }

    /** Same as stop(), plus prevents other handlers on the same layer. */
    public void stopImmediate() { stoppedImmediate = true; stopped = true; }

    /** @return true if propagation has been stopped in the current phase. */
    public boolean isStopped() { return stopped; }

    /** @return true if propagation has been stopped immediately in the current phase. */
    public boolean isStoppedImmediate() { return stoppedImmediate; }

    /** Marks the event as handled. Informational only - does not stop propagation. */
    public void markHandled() { handled = true; }

    /** @return true if some layer has marked this event as handled. */
    public boolean isHandled() { return handled; }

    /** Annotates context during capture for downstream layers to read during bubble. */
    public void put(String key, Object value) { context.put(key, value); }

    /** @return the context value stored under key, cast to type, or null if absent. */
    public <T> T get(String key, Class<T> type) {
        Object val = context.get(key);
        return val != null ? type.cast(val) : null;
    }

    /** @return true if a context value is stored under key. */
    public boolean has(String key) { return context.containsKey(key); }

    /** Resets propagation flags for the bubble phase. Context and handled state persist. */
    void resetForBubble() {
        stopped = false;
        stoppedImmediate = false;
    }

    public void markCaptureStart(){
        this.phase = CAPTURE;
        this.captureNanos = System.nanoTime();
    }
    public void markBubbleStart(){
        this.phase = InputPhase.BUBBLE;
        this.bubbleNanos = System.nanoTime();
    }
    public void markFinished(){
        this.phase = InputPhase.COMPLETE;
        this.completeNanos = System.nanoTime();
    }
    public long createNanos() { return createNanos; }
    public long captureNanos() { return captureNanos; }
    public long bubbleNanos() { return bubbleNanos; }
    public long completeNanos() { return completeNanos; }
    public InputPhase phase() { return phase; }

    public void setPhase(InputPhase inputPhase) {
        if(this.phase == PENDING && inputPhase == CAPTURE)
            markCaptureStart();
        
    }
}
