package io.github.yetyman.glfw.enums;

import java.util.*;

/**
 * Type-safe constants for GLFWMouseButton
 * Generated from jextract bindings
 */
public record GLFWMouseButton(int value) {

    public static final GLFWMouseButton GLFW_MOUSE_BUTTON_1 = new GLFWMouseButton(0);
    public static final GLFWMouseButton GLFW_MOUSE_BUTTON_2 = new GLFWMouseButton(1);
    public static final GLFWMouseButton GLFW_MOUSE_BUTTON_3 = new GLFWMouseButton(2);
    public static final GLFWMouseButton GLFW_MOUSE_BUTTON_4 = new GLFWMouseButton(3);
    public static final GLFWMouseButton GLFW_MOUSE_BUTTON_5 = new GLFWMouseButton(4);
    public static final GLFWMouseButton GLFW_MOUSE_BUTTON_6 = new GLFWMouseButton(5);
    public static final GLFWMouseButton GLFW_MOUSE_BUTTON_7 = new GLFWMouseButton(6);
    public static final GLFWMouseButton GLFW_MOUSE_BUTTON_8 = new GLFWMouseButton(7);
    public static final GLFWMouseButton GLFW_MOUSE_BUTTON_LAST = GLFW_MOUSE_BUTTON_8;
    public static final GLFWMouseButton GLFW_MOUSE_BUTTON_LEFT = GLFW_MOUSE_BUTTON_1;
    public static final GLFWMouseButton GLFW_MOUSE_BUTTON_MIDDLE = GLFW_MOUSE_BUTTON_3;
    public static final GLFWMouseButton GLFW_MOUSE_BUTTON_RIGHT = GLFW_MOUSE_BUTTON_2;

    public static GLFWMouseButton fromValue(int value) {
        return switch (value) {
            case 0 -> GLFW_MOUSE_BUTTON_1;
            case 1 -> GLFW_MOUSE_BUTTON_2;
            case 2 -> GLFW_MOUSE_BUTTON_3;
            case 3 -> GLFW_MOUSE_BUTTON_4;
            case 4 -> GLFW_MOUSE_BUTTON_5;
            case 5 -> GLFW_MOUSE_BUTTON_6;
            case 6 -> GLFW_MOUSE_BUTTON_7;
            case 7 -> GLFW_MOUSE_BUTTON_8;
            default -> new GLFWMouseButton(value);
        };
    }
}
