package io.github.yetyman.glfw.enums;

import java.util.*;

/**
 * Type-safe constants for GLFWGamepad
 * Generated from jextract bindings
 */
public record GLFWGamepad(int value) {

    public static final GLFWGamepad GLFW_GAMEPAD_AXIS_LAST = new GLFWGamepad(5);
    public static final GLFWGamepad GLFW_GAMEPAD_AXIS_LEFT_TRIGGER = new GLFWGamepad(4);
    public static final GLFWGamepad GLFW_GAMEPAD_AXIS_LEFT_X = new GLFWGamepad(0);
    public static final GLFWGamepad GLFW_GAMEPAD_AXIS_LEFT_Y = new GLFWGamepad(1);
    public static final GLFWGamepad GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER = GLFW_GAMEPAD_AXIS_LAST;
    public static final GLFWGamepad GLFW_GAMEPAD_AXIS_RIGHT_X = new GLFWGamepad(2);
    public static final GLFWGamepad GLFW_GAMEPAD_AXIS_RIGHT_Y = new GLFWGamepad(3);
    public static final GLFWGamepad GLFW_GAMEPAD_BUTTON_A = GLFW_GAMEPAD_AXIS_LEFT_X;
    public static final GLFWGamepad GLFW_GAMEPAD_BUTTON_B = GLFW_GAMEPAD_AXIS_LEFT_Y;
    public static final GLFWGamepad GLFW_GAMEPAD_BUTTON_BACK = new GLFWGamepad(6);
    public static final GLFWGamepad GLFW_GAMEPAD_BUTTON_CIRCLE = GLFW_GAMEPAD_AXIS_LEFT_Y;
    public static final GLFWGamepad GLFW_GAMEPAD_BUTTON_CROSS = GLFW_GAMEPAD_AXIS_LEFT_X;
    public static final GLFWGamepad GLFW_GAMEPAD_BUTTON_DPAD_DOWN = new GLFWGamepad(13);
    public static final GLFWGamepad GLFW_GAMEPAD_BUTTON_DPAD_LEFT = new GLFWGamepad(14);
    public static final GLFWGamepad GLFW_GAMEPAD_BUTTON_DPAD_RIGHT = new GLFWGamepad(12);
    public static final GLFWGamepad GLFW_GAMEPAD_BUTTON_DPAD_UP = new GLFWGamepad(11);
    public static final GLFWGamepad GLFW_GAMEPAD_BUTTON_GUIDE = new GLFWGamepad(8);
    public static final GLFWGamepad GLFW_GAMEPAD_BUTTON_LAST = GLFW_GAMEPAD_BUTTON_DPAD_LEFT;
    public static final GLFWGamepad GLFW_GAMEPAD_BUTTON_LEFT_BUMPER = GLFW_GAMEPAD_AXIS_LEFT_TRIGGER;
    public static final GLFWGamepad GLFW_GAMEPAD_BUTTON_LEFT_THUMB = new GLFWGamepad(9);
    public static final GLFWGamepad GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER = GLFW_GAMEPAD_AXIS_LAST;
    public static final GLFWGamepad GLFW_GAMEPAD_BUTTON_RIGHT_THUMB = new GLFWGamepad(10);
    public static final GLFWGamepad GLFW_GAMEPAD_BUTTON_SQUARE = GLFW_GAMEPAD_AXIS_RIGHT_X;
    public static final GLFWGamepad GLFW_GAMEPAD_BUTTON_START = new GLFWGamepad(7);
    public static final GLFWGamepad GLFW_GAMEPAD_BUTTON_TRIANGLE = GLFW_GAMEPAD_AXIS_RIGHT_Y;
    public static final GLFWGamepad GLFW_GAMEPAD_BUTTON_X = GLFW_GAMEPAD_AXIS_RIGHT_X;
    public static final GLFWGamepad GLFW_GAMEPAD_BUTTON_Y = GLFW_GAMEPAD_AXIS_RIGHT_Y;

    public static GLFWGamepad fromValue(int value) {
        return switch (value) {
            case 5 -> GLFW_GAMEPAD_AXIS_LAST;
            case 4 -> GLFW_GAMEPAD_AXIS_LEFT_TRIGGER;
            case 0 -> GLFW_GAMEPAD_BUTTON_A;
            case 1 -> GLFW_GAMEPAD_BUTTON_B;
            case 2 -> GLFW_GAMEPAD_BUTTON_X;
            case 3 -> GLFW_GAMEPAD_BUTTON_Y;
            case 6 -> GLFW_GAMEPAD_BUTTON_BACK;
            case 13 -> GLFW_GAMEPAD_BUTTON_DPAD_DOWN;
            case 14 -> GLFW_GAMEPAD_BUTTON_LAST;
            case 12 -> GLFW_GAMEPAD_BUTTON_DPAD_RIGHT;
            case 11 -> GLFW_GAMEPAD_BUTTON_DPAD_UP;
            case 8 -> GLFW_GAMEPAD_BUTTON_GUIDE;
            case 9 -> GLFW_GAMEPAD_BUTTON_LEFT_THUMB;
            case 10 -> GLFW_GAMEPAD_BUTTON_RIGHT_THUMB;
            case 7 -> GLFW_GAMEPAD_BUTTON_START;
            default -> new GLFWGamepad(value);
        };
    }
}
