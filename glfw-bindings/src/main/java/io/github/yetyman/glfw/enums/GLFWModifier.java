package io.github.yetyman.glfw.enums;

import java.util.*;

/**
 * Type-safe constants for GLFWModifier
 * Generated from jextract bindings
 */
public record GLFWModifier(int value) {

    public static final GLFWModifier GLFW_MOD_ALT = new GLFWModifier(4);
    public static final GLFWModifier GLFW_MOD_CAPS_LOCK = new GLFWModifier(16);
    public static final GLFWModifier GLFW_MOD_CONTROL = new GLFWModifier(2);
    public static final GLFWModifier GLFW_MOD_NUM_LOCK = new GLFWModifier(32);
    public static final GLFWModifier GLFW_MOD_SHIFT = new GLFWModifier(1);
    public static final GLFWModifier GLFW_MOD_SUPER = new GLFWModifier(8);

    public static GLFWModifier fromValue(int value) {
        return switch (value) {
            case 4 -> GLFW_MOD_ALT;
            case 16 -> GLFW_MOD_CAPS_LOCK;
            case 2 -> GLFW_MOD_CONTROL;
            case 32 -> GLFW_MOD_NUM_LOCK;
            case 1 -> GLFW_MOD_SHIFT;
            case 8 -> GLFW_MOD_SUPER;
            default -> new GLFWModifier(value);
        };
    }
}
