package io.github.yetyman.vulkan.input.events;

public record MouseMoveEvent(double x, double y, long timestamp) implements InputEvent {}