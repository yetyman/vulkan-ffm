package io.github.yetyman.glfw.enums;

import java.util.*;

/**
 * Type-safe constants for GLFWJoystick
 * Generated from jextract bindings
 */
public record GLFWJoystick(int value) {

    public static final GLFWJoystick GLFW_JOYSTICK_1 = new GLFWJoystick(0);
    public static final GLFWJoystick GLFW_JOYSTICK_10 = new GLFWJoystick(9);
    public static final GLFWJoystick GLFW_JOYSTICK_11 = new GLFWJoystick(10);
    public static final GLFWJoystick GLFW_JOYSTICK_12 = new GLFWJoystick(11);
    public static final GLFWJoystick GLFW_JOYSTICK_13 = new GLFWJoystick(12);
    public static final GLFWJoystick GLFW_JOYSTICK_14 = new GLFWJoystick(13);
    public static final GLFWJoystick GLFW_JOYSTICK_15 = new GLFWJoystick(14);
    public static final GLFWJoystick GLFW_JOYSTICK_16 = new GLFWJoystick(15);
    public static final GLFWJoystick GLFW_JOYSTICK_2 = new GLFWJoystick(1);
    public static final GLFWJoystick GLFW_JOYSTICK_3 = new GLFWJoystick(2);
    public static final GLFWJoystick GLFW_JOYSTICK_4 = new GLFWJoystick(3);
    public static final GLFWJoystick GLFW_JOYSTICK_5 = new GLFWJoystick(4);
    public static final GLFWJoystick GLFW_JOYSTICK_6 = new GLFWJoystick(5);
    public static final GLFWJoystick GLFW_JOYSTICK_7 = new GLFWJoystick(6);
    public static final GLFWJoystick GLFW_JOYSTICK_8 = new GLFWJoystick(7);
    public static final GLFWJoystick GLFW_JOYSTICK_9 = new GLFWJoystick(8);
    public static final GLFWJoystick GLFW_JOYSTICK_HAT_BUTTONS = new GLFWJoystick(327681);
    public static final GLFWJoystick GLFW_JOYSTICK_LAST = GLFW_JOYSTICK_16;

    public static GLFWJoystick fromValue(int value) {
        return switch (value) {
            case 0 -> GLFW_JOYSTICK_1;
            case 9 -> GLFW_JOYSTICK_10;
            case 10 -> GLFW_JOYSTICK_11;
            case 11 -> GLFW_JOYSTICK_12;
            case 12 -> GLFW_JOYSTICK_13;
            case 13 -> GLFW_JOYSTICK_14;
            case 14 -> GLFW_JOYSTICK_15;
            case 15 -> GLFW_JOYSTICK_16;
            case 1 -> GLFW_JOYSTICK_2;
            case 2 -> GLFW_JOYSTICK_3;
            case 3 -> GLFW_JOYSTICK_4;
            case 4 -> GLFW_JOYSTICK_5;
            case 5 -> GLFW_JOYSTICK_6;
            case 6 -> GLFW_JOYSTICK_7;
            case 7 -> GLFW_JOYSTICK_8;
            case 8 -> GLFW_JOYSTICK_9;
            case 327681 -> GLFW_JOYSTICK_HAT_BUTTONS;
            default -> new GLFWJoystick(value);
        };
    }
}
