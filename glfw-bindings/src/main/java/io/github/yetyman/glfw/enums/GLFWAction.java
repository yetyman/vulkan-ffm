package io.github.yetyman.glfw.enums;

import java.util.*;

/**
 * Type-safe constants for GLFWAction
 * Generated from jextract bindings
 */
public record GLFWAction(int value) {

    public static final GLFWAction GLFW_PRESS = new GLFWAction(1);
    public static final GLFWAction GLFW_RELEASE = new GLFWAction(0);
    public static final GLFWAction GLFW_REPEAT = new GLFWAction(2);

    public static GLFWAction fromValue(int value) {
        return switch (value) {
            case 1 -> GLFW_PRESS;
            case 0 -> GLFW_RELEASE;
            case 2 -> GLFW_REPEAT;
            default -> new GLFWAction(value);
        };
    }
}
