package io.github.yetyman.vulkan.foundation.ui.input;

/**
 * Input event passed through the capture/bubble dual-pass dispatch.
 *
 * Contains the event type and phase, a PropagationState with stop/context/handled tracking,
 * and event-specific payload fields (mouse position, key code, etc). Only the fields relevant
 * to type() are meaningful; all others hold their default value.
 *
 * Layers inspect phase() to determine whether they are in capture or bubble.
 * Layers call the propagation convenience methods to control flow.
 */
public class UIInputEvent {

    /** Bitmask flag: SHIFT modifier held. */
    public static final int MOD_SHIFT = 0x0001;
    /** Bitmask flag: CTRL modifier held. */
    public static final int MOD_CONTROL = 0x0002;
    /** Bitmask flag: ALT modifier held. */
    public static final int MOD_ALT = 0x0004;
    /** Bitmask flag: SUPER/META/WIN modifier held. */
    public static final int MOD_SUPER = 0x0008;

    private final InputEventType type;
    private final PropagationState propagation;
    private InputPhase phase;

    // Key event data
    private final int keyCode;
    private final int scanCode;
    private final int modifiers;

    // Mouse event data
    private final float mouseX;
    private final float mouseY;
    private final float deltaX;
    private final float deltaY;
    private final int mouseButton;

    // Scroll data
    private final float scrollX;
    private final float scrollY;

    // Text input
    private final int codepoint;

    // Gamepad data
    private final int gamepadId;
    private final int gamepadButton;
    private final int gamepadAxis;
    private final float gamepadAxisValue;

    // Touch data
    private final int touchId;

    // Window resize data
    private final int windowWidth;
    private final int windowHeight;

    // Drop file data
    private final String[] droppedFiles;

    private final long timestampNanos;

    private UIInputEvent(Builder b) {
        this.type = b.type;
        this.propagation = new PropagationState();
        this.phase = InputPhase.CAPTURE;
        this.keyCode = b.keyCode;
        this.scanCode = b.scanCode;
        this.modifiers = b.modifiers;
        this.mouseX = b.mouseX;
        this.mouseY = b.mouseY;
        this.deltaX = b.deltaX;
        this.deltaY = b.deltaY;
        this.mouseButton = b.mouseButton;
        this.scrollX = b.scrollX;
        this.scrollY = b.scrollY;
        this.codepoint = b.codepoint;
        this.gamepadId = b.gamepadId;
        this.gamepadButton = b.gamepadButton;
        this.gamepadAxis = b.gamepadAxis;
        this.gamepadAxisValue = b.gamepadAxisValue;
        this.touchId = b.touchId;
        this.windowWidth = b.windowWidth;
        this.windowHeight = b.windowHeight;
        this.droppedFiles = b.droppedFiles;
        this.timestampNanos = b.timestampNanos != 0 ? b.timestampNanos : System.nanoTime();
    }

    // --- Static factories ---

    public static UIInputEvent keyPress(int keyCode, int scanCode, int modifiers) {
        return new Builder(InputEventType.KEY_PRESS).keyCode(keyCode).scanCode(scanCode).modifiers(modifiers).build();
    }

    public static UIInputEvent keyRelease(int keyCode, int scanCode, int modifiers) {
        return new Builder(InputEventType.KEY_RELEASE).keyCode(keyCode).scanCode(scanCode).modifiers(modifiers).build();
    }

    public static UIInputEvent keyRepeat(int keyCode, int scanCode, int modifiers) {
        return new Builder(InputEventType.KEY_REPEAT).keyCode(keyCode).scanCode(scanCode).modifiers(modifiers).build();
    }

    public static UIInputEvent charInput(int codepoint) {
        return new Builder(InputEventType.CHAR_INPUT).codepoint(codepoint).build();
    }

    public static UIInputEvent mouseMove(float x, float y, float dx, float dy) {
        return new Builder(InputEventType.MOUSE_MOVE).mousePos(x, y).mouseDelta(dx, dy).build();
    }

    public static UIInputEvent mouseButton(int button, float x, float y, boolean pressed) {
        return new Builder(pressed ? InputEventType.MOUSE_BUTTON_PRESS : InputEventType.MOUSE_BUTTON_RELEASE)
            .mouseButton(button).mousePos(x, y).build();
    }

    public static UIInputEvent mouseEnter(float x, float y) {
        return new Builder(InputEventType.MOUSE_ENTER).mousePos(x, y).build();
    }

    public static UIInputEvent mouseLeave(float x, float y) {
        return new Builder(InputEventType.MOUSE_LEAVE).mousePos(x, y).build();
    }

    public static UIInputEvent scroll(float x, float y, float scrollX, float scrollY) {
        return new Builder(InputEventType.SCROLL).mousePos(x, y).scroll(scrollX, scrollY).build();
    }

    public static UIInputEvent gamepadButton(int gamepadId, int button, boolean pressed) {
        return new Builder(InputEventType.GAMEPAD_BUTTON).gamepadId(gamepadId).gamepadButton(button)
            .modifiers(pressed ? 1 : 0).build();
    }

    public static UIInputEvent gamepadAxis(int gamepadId, int axis, float value) {
        return new Builder(InputEventType.GAMEPAD_AXIS).gamepadId(gamepadId).gamepadAxis(axis)
            .gamepadAxisValue(value).build();
    }

    public static UIInputEvent touchBegin(int touchId, float x, float y) {
        return new Builder(InputEventType.TOUCH_BEGIN).touchId(touchId).mousePos(x, y).build();
    }

    public static UIInputEvent touchMove(int touchId, float x, float y, float dx, float dy) {
        return new Builder(InputEventType.TOUCH_MOVE).touchId(touchId).mousePos(x, y).mouseDelta(dx, dy).build();
    }

    public static UIInputEvent touchEnd(int touchId, float x, float y) {
        return new Builder(InputEventType.TOUCH_END).touchId(touchId).mousePos(x, y).build();
    }

    public static UIInputEvent focusGained() {
        return new Builder(InputEventType.FOCUS_GAINED).build();
    }

    public static UIInputEvent focusLost() {
        return new Builder(InputEventType.FOCUS_LOST).build();
    }

    public static UIInputEvent windowResize(int width, int height) {
        return new Builder(InputEventType.WINDOW_RESIZE).windowSize(width, height).build();
    }

    public static UIInputEvent dropFile(String[] files) {
        return new Builder(InputEventType.DROP_FILE).droppedFiles(files).build();
    }

    // --- Accessors ---

    public InputEventType type() { return type; }
    public InputPhase phase() { return phase; }
    public PropagationState propagation() { return propagation; }

    public int keyCode() { return keyCode; }
    public int scanCode() { return scanCode; }
    public int modifiers() { return modifiers; }

    public float mouseX() { return mouseX; }
    public float mouseY() { return mouseY; }
    public float deltaX() { return deltaX; }
    public float deltaY() { return deltaY; }
    public int mouseButton() { return mouseButton; }

    public float scrollX() { return scrollX; }
    public float scrollY() { return scrollY; }

    public int codepoint() { return codepoint; }

    public int gamepadId() { return gamepadId; }
    public int gamepadButton() { return gamepadButton; }
    public int gamepadAxis() { return gamepadAxis; }
    public float gamepadAxisValue() { return gamepadAxisValue; }

    public int touchId() { return touchId; }

    public int windowWidth() { return windowWidth; }
    public int windowHeight() { return windowHeight; }

    public String[] droppedFiles() { return droppedFiles; }

    public long timestampNanos() { return timestampNanos; }

    /** @return true if the SHIFT modifier bit is set. */
    public boolean isShiftDown() { return (modifiers & MOD_SHIFT) != 0; }
    /** @return true if the CTRL modifier bit is set. */
    public boolean isControlDown() { return (modifiers & MOD_CONTROL) != 0; }
    /** @return true if the ALT modifier bit is set. */
    public boolean isAltDown() { return (modifiers & MOD_ALT) != 0; }
    /** @return true if the SUPER/META modifier bit is set. */
    public boolean isSuperDown() { return (modifiers & MOD_SUPER) != 0; }

    /** Sets the active dispatch phase. Called by UIInputDispatcher only. */
    void setPhase(InputPhase phase) { this.phase = phase; }

    // --- Convenience propagation methods (delegate to propagation state) ---

    /** Prevents further layers from seeing this event in the current phase. */
    public void stopPropagation() { propagation.stop(); }

    /** Same as stopPropagation(), plus prevents other handlers on the same layer. */
    public void stopImmediatePropagation() { propagation.stopImmediate(); }

    /** Marks the event as handled. Informational only - does not stop propagation. */
    public void markHandled() { propagation.markHandled(); }

    /** @return true if some layer has marked this event as handled. */
    public boolean isHandled() { return propagation.isHandled(); }

    private static class Builder {
        private final InputEventType type;
        private int keyCode;
        private int scanCode;
        private int modifiers;
        private float mouseX;
        private float mouseY;
        private float deltaX;
        private float deltaY;
        private int mouseButton;
        private float scrollX;
        private float scrollY;
        private int codepoint;
        private int gamepadId;
        private int gamepadButton;
        private int gamepadAxis;
        private float gamepadAxisValue;
        private int touchId;
        private int windowWidth;
        private int windowHeight;
        private String[] droppedFiles;
        private long timestampNanos;

        private Builder(InputEventType type) { this.type = type; }

        Builder keyCode(int v) { this.keyCode = v; return this; }
        Builder scanCode(int v) { this.scanCode = v; return this; }
        Builder modifiers(int v) { this.modifiers = v; return this; }
        Builder mousePos(float x, float y) { this.mouseX = x; this.mouseY = y; return this; }
        Builder mouseDelta(float dx, float dy) { this.deltaX = dx; this.deltaY = dy; return this; }
        Builder mouseButton(int v) { this.mouseButton = v; return this; }
        Builder scroll(float x, float y) { this.scrollX = x; this.scrollY = y; return this; }
        Builder codepoint(int v) { this.codepoint = v; return this; }
        Builder gamepadId(int v) { this.gamepadId = v; return this; }
        Builder gamepadButton(int v) { this.gamepadButton = v; return this; }
        Builder gamepadAxis(int v) { this.gamepadAxis = v; return this; }
        Builder gamepadAxisValue(float v) { this.gamepadAxisValue = v; return this; }
        Builder touchId(int v) { this.touchId = v; return this; }
        Builder windowSize(int w, int h) { this.windowWidth = w; this.windowHeight = h; return this; }
        Builder droppedFiles(String[] v) { this.droppedFiles = v; return this; }

        UIInputEvent build() { return new UIInputEvent(this); }
    }
}
