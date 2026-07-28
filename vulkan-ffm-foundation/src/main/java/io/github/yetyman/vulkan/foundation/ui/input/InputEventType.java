package io.github.yetyman.vulkan.foundation.ui.input;

/**
 * Discriminates the kind of input event carried by an InputEvent.
 *
 * Pointer events (POINTER_DOWN/MOVE/UP) unify mouse, touch, and stylus —
 * the specific device is identified by PointerType on the event data, not the event type.
 */
public enum InputEventType {
    KEY_PRESS,
    KEY_RELEASE,
    KEY_REPEAT,
    CHAR_INPUT,
    POINTER_DOWN,
    POINTER_UP,
    POINTER_MOVE,
    MOUSE_ENTER,
    MOUSE_LEAVE,
    SCROLL,
    GAMEPAD_BUTTON,
    GAMEPAD_AXIS,
    FOCUS_GAINED,
    FOCUS_LOST,
    WINDOW_RESIZE,
    DROP_FILE
}
