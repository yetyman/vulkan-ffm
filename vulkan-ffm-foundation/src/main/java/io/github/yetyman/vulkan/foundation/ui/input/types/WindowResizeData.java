package io.github.yetyman.vulkan.foundation.ui.input.types;

import io.github.yetyman.vulkan.foundation.ui.input.InputEvent;
import io.github.yetyman.vulkan.foundation.ui.input.InputEventType;

/**
 * Window resize event data.
 */
public final class WindowResizeData extends InputEventData {
    public final int width;
    public final int height;

    public WindowResizeData(int width, int height) {
        this.width = width;
        this.height = height;
    }

    // --- Event factory ---

    public static InputEvent resize(int width, int height) {
        return new InputEvent(InputEventType.WINDOW_RESIZE, new WindowResizeData(width, height));
    }
}
