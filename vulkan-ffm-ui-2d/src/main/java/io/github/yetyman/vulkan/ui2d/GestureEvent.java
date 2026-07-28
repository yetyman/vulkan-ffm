package io.github.yetyman.vulkan.ui2d;

import io.github.yetyman.vulkan.foundation.ecs.Event;
import io.github.yetyman.vulkan.foundation.ecs.EventType;
import io.github.yetyman.vulkan.foundation.ecs.Node;

/**
 * Synthetic gesture events fired by the GestureRecognizer.
 * These are higher-level events derived from raw pointer events.
 */
public class GestureEvent extends Event {

    public static final EventType<GestureEvent> PRESS = EventType.create("press");
    public static final EventType<GestureEvent> DRAG_START = EventType.create("drag-start");
    public static final EventType<GestureEvent> DRAG_MOVE = EventType.create("drag-move");
    public static final EventType<GestureEvent> DRAG_END = EventType.create("drag-end");

    private final float x;
    private final float y;
    private final float startX; // where the gesture originated
    private final float startY;
    private final int button;
    private final int pointerId;
    private final Node target; // the node that "owns" this gesture (capture target)

    public GestureEvent(EventType<GestureEvent> type, float x, float y,
                        float startX, float startY, int button, int pointerId, Node target) {
        super(type);
        this.x = x;
        this.y = y;
        this.startX = startX;
        this.startY = startY;
        this.button = button;
        this.pointerId = pointerId;
        this.target = target;
    }

    public float x() { return x; }
    public float y() { return y; }
    public float startX() { return startX; }
    public float startY() { return startY; }
    public int button() { return button; }
    public int pointerId() { return pointerId; }
    public Node target() { return target; }

    /** @return distance from start to current position. */
    public float dragDistance() {
        float dx = x - startX;
        float dy = y - startY;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
