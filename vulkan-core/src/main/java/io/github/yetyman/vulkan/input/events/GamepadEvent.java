package io.github.yetyman.vulkan.input.events;

public sealed interface GamepadEvent extends InputEvent permits GamepadButtonEvent, GamepadAxisEvent, GamepadConnectEvent {
}

record GamepadButtonEvent(int gamepad, int button, int action, long timestamp) implements GamepadEvent {}
record GamepadAxisEvent(int gamepad, int axis, float value, long timestamp) implements GamepadEvent {}
record GamepadConnectEvent(int gamepad, boolean connected, long timestamp) implements GamepadEvent {}