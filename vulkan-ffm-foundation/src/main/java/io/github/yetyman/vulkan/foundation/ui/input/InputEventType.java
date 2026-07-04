package io.github.yetyman.vulkan.foundation.ui.input;

/** Discriminates the kind of input event carried by a UIInputEvent. */
public enum InputEventType {
    KEY_PRESS,
    KEY_RELEASE,
    KEY_REPEAT,
    CHAR_INPUT,
    MOUSE_BUTTON_PRESS,
    MOUSE_BUTTON_RELEASE,
    MOUSE_MOVE,
    MOUSE_ENTER,
    MOUSE_LEAVE,
    SCROLL,
    GAMEPAD_BUTTON,
    GAMEPAD_AXIS,
    TOUCH_BEGIN,
    TOUCH_MOVE,
    TOUCH_END,
    FOCUS_GAINED,
    FOCUS_LOST,
    WINDOW_RESIZE,
    DROP_FILE
}
