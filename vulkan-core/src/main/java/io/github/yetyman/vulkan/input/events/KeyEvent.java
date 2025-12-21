package io.github.yetyman.vulkan.input.events;

public record KeyEvent(int key, int scancode, int action, int mods, long timestamp) implements InputEvent {}