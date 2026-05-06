package io.github.yetyman.structures.input;

import io.github.yetyman.structures.state.StateRegistry;
import io.github.yetyman.structures.state.StateSlot;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Observable keyboard state backed by a {@link StateRegistry}.
 * <p>
 * Keys are registered iteratively from a provided collection of names — no hardcoded key list.
 * The name strings are application-defined (e.g. "W", "SPACE", "SHIFT_LEFT") and must match
 * whatever strings the input system uses when calling {@link #updateKey}.
 * <p>
 * Per tracked key:
 * <ul>
 *   <li>{@code key.<name>} — enum {@link KeyState} UP/DOWN</li>
 *   <li>{@code key.<name>.last_press} — long, {@code System.nanoTime()} at last DOWN event,
 *       {@code Long.MIN_VALUE} if never pressed. Hold duration = {@code now - lastPress.getValue()}.</li>
 * </ul>
 * <p>
 * Consumers should cache slot handles from {@link #keySlot} and {@link #lastPressSlot} for hot-path reads.
 */
public class KeyboardState {

    public enum KeyState { UP, DOWN }

    // -------------------------------------------------------------------------
    // Registry
    // -------------------------------------------------------------------------

    public final StateRegistry registry;

    // -------------------------------------------------------------------------
    // Slot maps — keyed by name
    // -------------------------------------------------------------------------

    public final Map<String, StateSlot.EnumSlot>  keySlots;
    public final Map<String, StateSlot.LongSlot>  lastPressSlots;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * @param keyNames names of all keys to track (e.g. List.of("W","A","S","D","SPACE"))
     */
    @SuppressWarnings("unchecked")
    public KeyboardState(Collection<String> keyNames) {
        registry = new StateRegistry();

        Map<String, StateSlot.EnumSlot> ks  = new HashMap<>(keyNames.size() * 2);
        Map<String, StateSlot.LongSlot> lps = new HashMap<>(keyNames.size() * 2);

        for (String name : keyNames) {
            ks.put(name,  registry.addState("key." + name, KeyState.class, KeyState.UP));
            lps.put(name, registry.addState("key." + name + ".last_press", Long.MIN_VALUE));
        }

        keySlots       = Collections.unmodifiableMap(ks);
        lastPressSlots = Collections.unmodifiableMap(lps);

        registry.seal();
    }

    // -------------------------------------------------------------------------
    // Feed-in API
    // -------------------------------------------------------------------------

    /**
     * Feed a key press or release.
     *
     * @param name the key name as registered at construction
     * @param down true if pressed, false if released
     * @param now  {@code System.nanoTime()} at event time
     */
    public void updateKey(String name, boolean down, long now) {
        StateSlot.EnumSlot ks = keySlots.get(name);
        if (ks == null) return; // untracked key — ignore

        registry.beginBatch();
        try {
            registry.set(ks, down ? KeyState.DOWN : KeyState.UP);
            if (down) registry.set(lastPressSlots.get(name), now);
        } finally {
            registry.endBatch();
        }
    }

    // -------------------------------------------------------------------------
    // Convenience accessors
    // -------------------------------------------------------------------------
    public KeyState keyState(String name) {
        StateSlot.EnumSlot s = keySlots.get(name);
        return s == null ? KeyState.UP : (KeyState) s.getValue();
    }

    /**
     * Convenience — nanos since last press, or {@code Long.MIN_VALUE} if never pressed.
     * Caller computes hold duration as {@code now - holdNanosStart(name)}.
     */
    public long holdNanosStart(String name) {
        StateSlot.LongSlot s = lastPressSlots.get(name);
        return s == null ? Long.MIN_VALUE : s.getValue();
    }
}
