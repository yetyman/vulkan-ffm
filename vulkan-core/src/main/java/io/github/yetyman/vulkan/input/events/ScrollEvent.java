package io.github.yetyman.vulkan.input.events;

public record ScrollEvent(double xOffset, double yOffset, long timestamp) implements InputEvent {}