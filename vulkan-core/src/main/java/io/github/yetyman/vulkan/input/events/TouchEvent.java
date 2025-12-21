package io.github.yetyman.vulkan.input.events;

public sealed interface TouchEvent extends InputEvent permits TouchDownEvent, TouchMoveEvent, TouchUpEvent {
}

record TouchDownEvent(int fingerId, double x, double y, float pressure, long timestamp) implements TouchEvent {}
record TouchMoveEvent(int fingerId, double x, double y, float pressure, long timestamp) implements TouchEvent {}
record TouchUpEvent(int fingerId, double x, double y, long timestamp) implements TouchEvent {}