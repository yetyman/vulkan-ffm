package io.github.yetyman.vulkan.input.events;

public sealed interface InputEvent permits KeyEvent, MouseButtonEvent, MouseMoveEvent, ScrollEvent, 
    WindowEvent, GamepadEvent, TouchEvent {
    long timestamp();
}