package io.github.yetyman.vulkan.foundation.ui.input.types;

import io.github.yetyman.vulkan.foundation.ui.input.InputEvent;
import io.github.yetyman.vulkan.foundation.ui.input.InputEventType;

/**
 * Mouse position, delta, and button event data.
 */
public final class MouseInputData extends InputEventData {
    public final float mouseX;
    public final float mouseY;
    public final float deltaX;
    public final float deltaY;
    public final int mouseButton;

    public MouseInputData(float mouseX, float mouseY, float deltaX, float deltaY, int mouseButton) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.deltaX = deltaX;
        this.deltaY = deltaY;
        this.mouseButton = mouseButton;
    }

    // --- Event factories ---

    public static InputEvent move(float x, float y, float dx, float dy) {
        return new InputEvent(InputEventType.MOUSE_MOVE, new MouseInputData(x, y, dx, dy, 0));
    }

    public static InputEvent buttonPress(int button, float x, float y) {
        return new InputEvent(InputEventType.MOUSE_BUTTON_PRESS, new MouseInputData(x, y, 0, 0, button));
    }

    public static InputEvent buttonRelease(int button, float x, float y) {
        return new InputEvent(InputEventType.MOUSE_BUTTON_RELEASE, new MouseInputData(x, y, 0, 0, button));
    }

    public static InputEvent enter(float x, float y) {
        return new InputEvent(InputEventType.MOUSE_ENTER, new MouseInputData(x, y, 0, 0, 0));
    }

    public static InputEvent leave(float x, float y) {
        return new InputEvent(InputEventType.MOUSE_LEAVE, new MouseInputData(x, y, 0, 0, 0));
    }
}
