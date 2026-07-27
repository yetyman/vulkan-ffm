package io.github.yetyman.vulkan.foundation.ui.input.types;

/**
 * Marker interface for composable input event data payloads.
 *
 * Each subclass carries only the fields relevant to one category of input,
 * keeping UIInputEvent lean and avoiding wasted memory on irrelevant defaults.
 */
public abstract class InputEventData {
}
