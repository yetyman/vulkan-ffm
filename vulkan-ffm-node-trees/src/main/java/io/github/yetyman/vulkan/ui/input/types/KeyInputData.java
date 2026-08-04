package io.github.yetyman.vulkan.ui.input.types;

import io.github.yetyman.vulkan.ui.input.InputEvent;
import io.github.yetyman.vulkan.ui.input.InputEventType;

/**
 * Key press/release/repeat event data.
 */
public final class KeyInputData extends InputEventData {

    /** Bitmask flag: SHIFT modifier held. */
    public static final int MOD_SHIFT = 0x0001;
    /** Bitmask flag: CTRL modifier held. */
    public static final int MOD_CONTROL = 0x0002;
    /** Bitmask flag: ALT modifier held. */
    public static final int MOD_ALT = 0x0004;
    /** Bitmask flag: SUPER/META/WIN modifier held. */
    public static final int MOD_SUPER = 0x0008;

    public final int keyCode;
    public final int scanCode;
    public final int modifiers;

    public KeyInputData(int keyCode, int scanCode, int modifiers) {
        this.keyCode = keyCode;
        this.scanCode = scanCode;
        this.modifiers = modifiers;
    }

    // --- Event factories ---

    public static InputEvent press(int keyCode, int scanCode, int modifiers) {
        return new InputEvent(InputEventType.KEY_PRESS, new KeyInputData(keyCode, scanCode, modifiers));
    }

    public static InputEvent release(int keyCode, int scanCode, int modifiers) {
        return new InputEvent(InputEventType.KEY_RELEASE, new KeyInputData(keyCode, scanCode, modifiers));
    }

    public static InputEvent repeat(int keyCode, int scanCode, int modifiers) {
        return new InputEvent(InputEventType.KEY_REPEAT, new KeyInputData(keyCode, scanCode, modifiers));
    }

    // --- Modifier mask queries ---

    /** @return true if the SHIFT modifier bit is set. */
    public boolean isShiftDown() { return (modifiers & MOD_SHIFT) != 0; }
    /** @return true if the CTRL modifier bit is set. */
    public boolean isControlDown() { return (modifiers & MOD_CONTROL) != 0; }
    /** @return true if the ALT modifier bit is set. */
    public boolean isAltDown() { return (modifiers & MOD_ALT) != 0; }
    /** @return true if the SUPER/META modifier bit is set. */
    public boolean isSuperDown() { return (modifiers & MOD_SUPER) != 0; }
}
