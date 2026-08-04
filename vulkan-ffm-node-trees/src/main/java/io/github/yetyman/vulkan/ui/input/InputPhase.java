package io.github.yetyman.vulkan.ui.input;

/** Identifies which pass of the capture/bubble dual-pass dispatch is currently active. */
public enum InputPhase {
    PENDING,
    /** Top-down traversal. Layers annotate context, may stop propagation early. */
    CAPTURE,
    /** Bottom-up traversal. Layers react to event with full context from capture. */
    BUBBLE,
    COMPLETE
}
