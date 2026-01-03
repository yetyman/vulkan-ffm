package io.github.yetyman.glfw.enums;

import java.util.*;

/**
 * Type-safe constants for GLFWConstants
 * Generated from jextract bindings
 */
public record GLFWConstants(int value) {

    public static final GLFWConstants GLFW_ANY_POSITION = new GLFWConstants(-2147483648);
    public static final GLFWConstants GLFW_DONT_CARE = new GLFWConstants(-1);
    public static final GLFWConstants GLFW_FALSE = new GLFWConstants(0);
    public static final GLFWConstants GLFW_TRUE = new GLFWConstants(1);

    public static GLFWConstants fromValue(int value) {
        return switch (value) {
            case -2147483648 -> GLFW_ANY_POSITION;
            case -1 -> GLFW_DONT_CARE;
            case 0 -> GLFW_FALSE;
            case 1 -> GLFW_TRUE;
            default -> new GLFWConstants(value);
        };
    }
}
