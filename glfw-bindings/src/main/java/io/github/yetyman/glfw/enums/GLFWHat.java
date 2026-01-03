package io.github.yetyman.glfw.enums;

import java.util.*;

/**
 * Type-safe constants for GLFWHat
 * Generated from jextract bindings
 */
public record GLFWHat(int value) {

    public static final GLFWHat GLFW_HAT_CENTERED = new GLFWHat(0);
    public static final GLFWHat GLFW_HAT_DOWN = new GLFWHat(4);
    public static final GLFWHat GLFW_HAT_LEFT = new GLFWHat(8);
    public static final GLFWHat GLFW_HAT_LEFT_DOWN = new GLFWHat(12);
    public static final GLFWHat GLFW_HAT_LEFT_UP = new GLFWHat(9);
    public static final GLFWHat GLFW_HAT_RIGHT = new GLFWHat(2);
    public static final GLFWHat GLFW_HAT_RIGHT_DOWN = new GLFWHat(6);
    public static final GLFWHat GLFW_HAT_RIGHT_UP = new GLFWHat(3);
    public static final GLFWHat GLFW_HAT_UP = new GLFWHat(1);

    public static GLFWHat fromValue(int value) {
        return switch (value) {
            case 0 -> GLFW_HAT_CENTERED;
            case 4 -> GLFW_HAT_DOWN;
            case 8 -> GLFW_HAT_LEFT;
            case 12 -> GLFW_HAT_LEFT_DOWN;
            case 9 -> GLFW_HAT_LEFT_UP;
            case 2 -> GLFW_HAT_RIGHT;
            case 6 -> GLFW_HAT_RIGHT_DOWN;
            case 3 -> GLFW_HAT_RIGHT_UP;
            case 1 -> GLFW_HAT_UP;
            default -> new GLFWHat(value);
        };
    }
}
