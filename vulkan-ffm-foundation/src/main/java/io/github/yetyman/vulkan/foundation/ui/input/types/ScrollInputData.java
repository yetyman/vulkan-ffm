package io.github.yetyman.vulkan.foundation.ui.input.types;

import io.github.yetyman.vulkan.foundation.ui.input.InputEvent;
import io.github.yetyman.vulkan.foundation.ui.input.InputEventType;

/**
 * Scroll wheel or trackpad scroll data, including cursor position at time of scroll.
 */
public final class ScrollInputData extends InputEventData {
    public final float mouseX;
    public final float mouseY;
    public final float scrollX;
    public final float scrollY;

    public ScrollInputData(float mouseX, float mouseY, float scrollX, float scrollY) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.scrollX = scrollX;
        this.scrollY = scrollY;
    }

    // --- Event factory ---

    public static InputEvent scroll(float mouseX, float mouseY, float scrollX, float scrollY) {
        return new InputEvent(InputEventType.SCROLL, new ScrollInputData(mouseX, mouseY, scrollX, scrollY));
    }
}
