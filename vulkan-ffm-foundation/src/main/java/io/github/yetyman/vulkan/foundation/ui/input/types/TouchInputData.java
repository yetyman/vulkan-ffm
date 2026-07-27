package io.github.yetyman.vulkan.foundation.ui.input.types;

import io.github.yetyman.vulkan.foundation.ui.input.InputEvent;
import io.github.yetyman.vulkan.foundation.ui.input.InputEventType;

/**
 * Touch event data (finger ID + position + delta).
 */
public final class TouchInputData extends InputEventData {
    public final int touchId;
    public final float x;
    public final float y;
    public final float deltaX;
    public final float deltaY;

    public TouchInputData(int touchId, float x, float y, float deltaX, float deltaY) {
        this.touchId = touchId;
        this.x = x;
        this.y = y;
        this.deltaX = deltaX;
        this.deltaY = deltaY;
    }

    // --- Event factories ---

    public static InputEvent begin(int touchId, float x, float y) {
        return new InputEvent(InputEventType.TOUCH_BEGIN, new TouchInputData(touchId, x, y, 0, 0));
    }

    public static InputEvent move(int touchId, float x, float y, float dx, float dy) {
        return new InputEvent(InputEventType.TOUCH_MOVE, new TouchInputData(touchId, x, y, dx, dy));
    }

    public static InputEvent end(int touchId, float x, float y) {
        return new InputEvent(InputEventType.TOUCH_END, new TouchInputData(touchId, x, y, 0, 0));
    }
}
