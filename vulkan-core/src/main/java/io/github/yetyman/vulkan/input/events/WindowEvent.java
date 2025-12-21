package io.github.yetyman.vulkan.input.events;

public sealed interface WindowEvent extends InputEvent permits WindowResizeEvent, WindowCloseEvent, WindowFocusEvent {
}

record WindowResizeEvent(int width, int height, long timestamp) implements WindowEvent {}
record WindowCloseEvent(long timestamp) implements WindowEvent {}
record WindowFocusEvent(boolean focused, long timestamp) implements WindowEvent {}