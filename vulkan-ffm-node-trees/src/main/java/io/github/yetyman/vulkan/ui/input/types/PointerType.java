package io.github.yetyman.vulkan.ui.input.types;

/**
 * Identifies the physical device category producing pointer events.
 * Layers that need device-specific behavior (hover detection, pressure painting)
 * inspect this; layers that don't simply read x/y/phase.
 */
public enum PointerType {
    MOUSE,
    TOUCH,
    STYLUS
}
