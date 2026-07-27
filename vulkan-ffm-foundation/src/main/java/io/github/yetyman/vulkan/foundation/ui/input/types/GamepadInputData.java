package io.github.yetyman.vulkan.foundation.ui.input.types;

import io.github.yetyman.vulkan.foundation.ui.input.InputEvent;
import io.github.yetyman.vulkan.foundation.ui.input.InputEventType;

/**
 * Gamepad button or axis event data.
 */
public final class GamepadInputData extends InputEventData {
    public final int gamepadId;
    public final int button;
    public final int axis;
    public final float axisValue;

    public GamepadInputData(int gamepadId, int button, int axis, float axisValue) {
        this.gamepadId = gamepadId;
        this.button = button;
        this.axis = axis;
        this.axisValue = axisValue;
    }

    // --- Event factories ---

    public static InputEvent buttonPress(int gamepadId, int button) {
        return new InputEvent(InputEventType.GAMEPAD_BUTTON, new GamepadInputData(gamepadId, button, 0, 1.0f));
    }

    public static InputEvent buttonRelease(int gamepadId, int button) {
        return new InputEvent(InputEventType.GAMEPAD_BUTTON, new GamepadInputData(gamepadId, button, 0, 0.0f));
    }

    public static InputEvent axis(int gamepadId, int axis, float value) {
        return new InputEvent(InputEventType.GAMEPAD_AXIS, new GamepadInputData(gamepadId, 0, axis, value));
    }
}
