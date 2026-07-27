package io.github.yetyman.vulkan.foundation.ui.input.types;

import io.github.yetyman.vulkan.foundation.ui.input.InputEvent;
import io.github.yetyman.vulkan.foundation.ui.input.InputEventType;

/**
 * Focus gained/lost event data. Carries no additional fields.
 */
public final class FocusData extends InputEventData {

    private static final FocusData INSTANCE = new FocusData();

    private FocusData() {}

    // --- Event factories ---

    public static InputEvent gained() {
        return new InputEvent(InputEventType.FOCUS_GAINED, INSTANCE);
    }

    public static InputEvent lost() {
        return new InputEvent(InputEventType.FOCUS_LOST, INSTANCE);
    }
}
