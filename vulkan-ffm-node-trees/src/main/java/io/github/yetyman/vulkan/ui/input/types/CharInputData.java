package io.github.yetyman.vulkan.ui.input.types;

import io.github.yetyman.vulkan.ui.input.InputEvent;
import io.github.yetyman.vulkan.ui.input.InputEventType;

/**
 * Character/text input data (Unicode codepoint from IME or keyboard).
 */
public final class CharInputData extends InputEventData {
    public final int codepoint;

    public CharInputData(int codepoint) {
        this.codepoint = codepoint;
    }

    // --- Event factory ---

    public static InputEvent input(int codepoint) {
        return new InputEvent(InputEventType.CHAR_INPUT, new CharInputData(codepoint));
    }
}
