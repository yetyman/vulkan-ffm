package io.github.yetyman.vulkan.input.events;

public record MouseButtonEvent(int button, int action, int mods, long timestamp) implements InputEvent {}