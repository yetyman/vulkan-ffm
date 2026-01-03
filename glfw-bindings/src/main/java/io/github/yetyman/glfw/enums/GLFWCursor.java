package io.github.yetyman.glfw.enums;

import java.util.*;

/**
 * Type-safe constants for GLFWCursor
 * Generated from jextract bindings
 */
public record GLFWCursor(int value) {

    public static final GLFWCursor GLFW_CURSOR_CAPTURED = new GLFWCursor(212996);
    public static final GLFWCursor GLFW_CURSOR_DISABLED = new GLFWCursor(212995);
    public static final GLFWCursor GLFW_CURSOR_HIDDEN = new GLFWCursor(212994);
    public static final GLFWCursor GLFW_CURSOR_NORMAL = new GLFWCursor(212993);

    public static GLFWCursor fromValue(int value) {
        return switch (value) {
            case 212996 -> GLFW_CURSOR_CAPTURED;
            case 212995 -> GLFW_CURSOR_DISABLED;
            case 212994 -> GLFW_CURSOR_HIDDEN;
            case 212993 -> GLFW_CURSOR_NORMAL;
            default -> new GLFWCursor(value);
        };
    }
}
