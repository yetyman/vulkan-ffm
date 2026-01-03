package io.github.yetyman.glfw.enums;

import java.util.*;

/**
 * Type-safe constants for GLFWConnectionEvent
 * Generated from jextract bindings
 */
public record GLFWConnectionEvent(int value) {

    public static final GLFWConnectionEvent GLFW_CONNECTED = new GLFWConnectionEvent(262145);
    public static final GLFWConnectionEvent GLFW_DISCONNECTED = new GLFWConnectionEvent(262146);

    public static GLFWConnectionEvent fromValue(int value) {
        return switch (value) {
            case 262145 -> GLFW_CONNECTED;
            case 262146 -> GLFW_DISCONNECTED;
            default -> new GLFWConnectionEvent(value);
        };
    }
}
