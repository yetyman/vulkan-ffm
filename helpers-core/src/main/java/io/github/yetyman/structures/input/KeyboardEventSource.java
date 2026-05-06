package io.github.yetyman.structures.input;

/**
 * Sink for raw keyboard events. Implement to feed a {@link KeyboardState} or any other consumer.
 * Decoupled from any windowing system.
 */
public interface KeyboardEventSource {
    void onKey(String name, boolean down, long now);
}
