package io.github.yetyman.vulkan.foundation.ecs;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Type token identifying a specific event kind. NOT Class-based - explicitly a custom
 * identity object that users create and hold references to.
 *
 * Open-ended for extension: any module can define new EventType instances for its own
 * event kinds without touching this class or extending it.
 *
 * Usage:
 * <pre>
 *   // Define event types as static constants
 *   public static final EventType&lt;ClickEvent&gt; CLICK = EventType.create("click");
 *   public static final EventType&lt;ScrollEvent&gt; SCROLL = EventType.create("scroll");
 *
 *   // Register handler for specific type
 *   node.addEventHandler(CLICK, event -&gt; { ... });
 *
 *   // Fire event
 *   node.fireEvent(CLICK, new ClickEvent(x, y, button));
 * </pre>
 *
 * @param <E> the concrete event class associated with this type
 */
public final class EventType<E extends Event> {

    private static final AtomicInteger ID_COUNTER = new AtomicInteger(0);

    private final int id;
    private final String name;

    private EventType(String name) {
        this.id = ID_COUNTER.getAndIncrement();
        this.name = name;
    }

    /**
     * Creates a new event type with the given debug name.
     * Each call creates a distinct type (identity-based, not name-based equality).
     *
     * @param name a human-readable name for debugging/logging
     * @param <E> the event class this type is associated with
     * @return a new unique event type
     */
    public static <E extends Event> EventType<E> create(String name) {
        return new EventType<>(name);
    }

    /** @return unique integer ID for this event type (useful for array-based dispatch). */
    public int id() { return id; }

    /** @return the debug name of this event type. */
    public String name() { return name; }

    @Override
    public String toString() {
        return "EventType{" + name + "#" + id + "}";
    }

    // Identity-based equality (default Object equals/hashCode is correct)
}
