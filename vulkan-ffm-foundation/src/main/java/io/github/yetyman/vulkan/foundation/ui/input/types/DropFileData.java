package io.github.yetyman.vulkan.foundation.ui.input.types;

import io.github.yetyman.vulkan.foundation.ui.input.InputEvent;
import io.github.yetyman.vulkan.foundation.ui.input.InputEventType;

/**
 * File drop event data (drag-and-drop from OS).
 */
public final class DropFileData extends InputEventData {
    public final String[] files;

    public DropFileData(String[] files) {
        this.files = files;
    }

    // --- Event factory ---

    public static InputEvent drop(String[] files) {
        return new InputEvent(InputEventType.DROP_FILE, new DropFileData(files));
    }
}
