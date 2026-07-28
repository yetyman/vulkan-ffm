package io.github.yetyman.vulkan.ui2d;

import io.github.yetyman.vulkan.foundation.ecs.Event;
import io.github.yetyman.vulkan.foundation.ecs.EventType;
import io.github.yetyman.vulkan.foundation.ecs.Node;

/**
 * Low-level pointer events dispatched by the UILayer after hit-testing.
 * The GestureRecognizer watches these and synthesizes higher-level events.
 */
public class PointerEvent extends Event {

    public static final EventType<PointerEvent> DOWN = EventType.create("pointer-down");
    public static final EventType<PointerEvent> MOVE = EventType.create("pointer-move");
    public static final EventType<PointerEvent> UP = EventType.create("pointer-up");
    public static final EventType<PointerEvent> ENTER = EventType.create("pointer-enter");
    public static final EventType<PointerEvent> EXIT = EventType.create("pointer-exit");

    private final float x;
    private final float y;
    private final int button; // 0=left, 1=right, 2=middle, -1=no button (move)
    private final int pointerId; // for multi-touch
    private final Node target; // the hit-tested target node

    public PointerEvent(EventType<PointerEvent> type, float x, float y, int button, int pointerId, Node target) {
        super(type);
        this.x = x;
        this.y = y;
        this.button = button;
        this.pointerId = pointerId;
        this.target = target;
    }

    public float x() { return x; }
    public float y() { return y; }
    public int button() { return button; }
    public int pointerId() { return pointerId; }
    public Node target() { return target; }
}
