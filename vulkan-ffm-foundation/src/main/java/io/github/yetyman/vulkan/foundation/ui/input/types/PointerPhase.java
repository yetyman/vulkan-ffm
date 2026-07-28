package io.github.yetyman.vulkan.foundation.ui.input.types;

/**
 * Lifecycle phase of a pointer contact.
 * Maps to platform concepts: GLFW button press/release, touch began/ended,
 * and the continuous MOVED state between them.
 */
public enum PointerPhase {
    /** Pointer contact began (mouse button down, finger touched screen). */
    BEGAN,
    /** Pointer moved while active (mouse move, finger dragged). */
    MOVED,
    /** Pointer contact ended normally (mouse button up, finger lifted). */
    ENDED,
    /** Pointer contact cancelled by system (touch interrupted, app backgrounded). */
    CANCELLED
}
