package io.github.yetyman.vulkan.ui.input.types;

import io.github.yetyman.vulkan.ui.input.InputEvent;
import io.github.yetyman.vulkan.ui.input.InputEventType;

/**
 * Unified pointer event data covering mouse, touch, and stylus input.
 *
 * Design follows the W3C Pointer Events model: all pointing devices produce
 * the same event structure, distinguished by {@link #pointerType}. This avoids
 * separate mouse/touch/pen event hierarchies and lets layers handle input
 * uniformly unless they specifically need device-aware behavior.
 *
 * Fields that don't apply to a device type carry neutral defaults:
 * - Mouse: pressure=0.5 when button pressed, tiltX/Y=0, width/height=1
 * - Touch: button=-1, tiltX/Y=0
 * - Stylus: width/height from contact area if available
 */
public class PointerInputData extends InputEventData {

    /** Unique identifier for this pointer across its lifetime (stable from BEGAN to ENDED). */
    public final int pointerId;

    /** Physical device category. */
    public final PointerType pointerType;

    /** Screen-space X coordinate in pixels. */
    public final float x;

    /** Screen-space Y coordinate in pixels. */
    public final float y;

    /** Delta X since last event for this pointer. */
    public final float deltaX;

    /** Delta Y since last event for this pointer. */
    public final float deltaY;

    /** Normalized pressure 0.0–1.0. Mouse: 0.5 when pressed, 0.0 when hovering. */
    public final float pressure;

    /** Lifecycle phase of this pointer contact. */
    public final PointerPhase phase;

    /** Tilt angle in degrees along the X axis (-90 to 90). Stylus only, 0 otherwise. */
    public final float tiltX;

    /** Tilt angle in degrees along the Y axis (-90 to 90). Stylus only, 0 otherwise. */
    public final float tiltY;

    /** Contact width in pixels (touch/stylus contact area). Mouse defaults to 1. */
    public final float width;

    /** Contact height in pixels (touch/stylus contact area). Mouse defaults to 1. */
    public final float height;

    /** Button index for button events (-1 for move/touch with no button concept). */
    public final int button;

    public PointerInputData(int pointerId, PointerType pointerType, float x, float y,
                            float deltaX, float deltaY, float pressure, PointerPhase phase,
                            float tiltX, float tiltY, float width, float height, int button) {
        this.pointerId = pointerId;
        this.pointerType = pointerType;
        this.x = x;
        this.y = y;
        this.deltaX = deltaX;
        this.deltaY = deltaY;
        this.pressure = pressure;
        this.phase = phase;
        this.tiltX = tiltX;
        this.tiltY = tiltY;
        this.width = width;
        this.height = height;
        this.button = button;
    }

    // --- Mouse event factories ---

    /** Mouse move (hovering, no button pressed). */
    public static InputEvent mouseMove(float x, float y, float dx, float dy) {
        return new InputEvent(InputEventType.POINTER_MOVE,
            new PointerInputData(0, PointerType.MOUSE, x, y, dx, dy, 0f, PointerPhase.MOVED,
                0f, 0f, 1f, 1f, -1));
    }

    /** Mouse button press. */
    public static InputEvent mousePress(int button, float x, float y) {
        return new InputEvent(InputEventType.POINTER_DOWN,
            new PointerInputData(0, PointerType.MOUSE, x, y, 0f, 0f, 0.5f, PointerPhase.BEGAN,
                0f, 0f, 1f, 1f, button));
    }

    /** Mouse button release. */
    public static InputEvent mouseRelease(int button, float x, float y) {
        return new InputEvent(InputEventType.POINTER_UP,
            new PointerInputData(0, PointerType.MOUSE, x, y, 0f, 0f, 0f, PointerPhase.ENDED,
                0f, 0f, 1f, 1f, button));
    }

    // --- Touch event factories ---

    /** Touch began. */
    public static InputEvent touchBegan(int pointerId, float x, float y, float pressure) {
        return new InputEvent(InputEventType.POINTER_DOWN,
            new PointerInputData(pointerId, PointerType.TOUCH, x, y, 0f, 0f, pressure, PointerPhase.BEGAN,
                0f, 0f, 10f, 10f, -1));
    }

    /** Touch moved. */
    public static InputEvent touchMoved(int pointerId, float x, float y, float dx, float dy, float pressure) {
        return new InputEvent(InputEventType.POINTER_MOVE,
            new PointerInputData(pointerId, PointerType.TOUCH, x, y, dx, dy, pressure, PointerPhase.MOVED,
                0f, 0f, 10f, 10f, -1));
    }

    /** Touch ended. */
    public static InputEvent touchEnded(int pointerId, float x, float y) {
        return new InputEvent(InputEventType.POINTER_UP,
            new PointerInputData(pointerId, PointerType.TOUCH, x, y, 0f, 0f, 0f, PointerPhase.ENDED,
                0f, 0f, 10f, 10f, -1));
    }

    /** Touch cancelled. */
    public static InputEvent touchCancelled(int pointerId, float x, float y) {
        return new InputEvent(InputEventType.POINTER_UP,
            new PointerInputData(pointerId, PointerType.TOUCH, x, y, 0f, 0f, 0f, PointerPhase.CANCELLED,
                0f, 0f, 10f, 10f, -1));
    }

    // --- Stylus event factories ---

    /** Stylus down with pressure and tilt. */
    public static InputEvent stylusDown(int pointerId, float x, float y, float pressure, float tiltX, float tiltY) {
        return new InputEvent(InputEventType.POINTER_DOWN,
            new PointerInputData(pointerId, PointerType.STYLUS, x, y, 0f, 0f, pressure, PointerPhase.BEGAN,
                tiltX, tiltY, 2f, 2f, -1));
    }

    /** Stylus moved with pressure and tilt. */
    public static InputEvent stylusMoved(int pointerId, float x, float y, float dx, float dy, float pressure, float tiltX, float tiltY) {
        return new InputEvent(InputEventType.POINTER_MOVE,
            new PointerInputData(pointerId, PointerType.STYLUS, x, y, dx, dy, pressure, PointerPhase.MOVED,
                tiltX, tiltY, 2f, 2f, -1));
    }

    /** Stylus up. */
    public static InputEvent stylusUp(int pointerId, float x, float y) {
        return new InputEvent(InputEventType.POINTER_UP,
            new PointerInputData(pointerId, PointerType.STYLUS, x, y, 0f, 0f, 0f, PointerPhase.ENDED,
                0f, 0f, 2f, 2f, -1));
    }
}
