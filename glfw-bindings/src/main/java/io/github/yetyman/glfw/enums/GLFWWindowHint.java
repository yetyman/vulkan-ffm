package io.github.yetyman.glfw.enums;

import java.util.*;

/**
 * Type-safe constants for GLFWWindowHint
 * Generated from jextract bindings
 */
public record GLFWWindowHint(int value) {

    public static final GLFWWindowHint GLFW_AUTO_ICONIFY = new GLFWWindowHint(131078);
    public static final GLFWWindowHint GLFW_DECORATED = new GLFWWindowHint(131077);
    public static final GLFWWindowHint GLFW_FLOATING = new GLFWWindowHint(131079);
    public static final GLFWWindowHint GLFW_FOCUSED = new GLFWWindowHint(131073);
    public static final GLFWWindowHint GLFW_FOCUS_ON_SHOW = new GLFWWindowHint(131084);
    public static final GLFWWindowHint GLFW_HOVERED = new GLFWWindowHint(131083);
    public static final GLFWWindowHint GLFW_ICONIFIED = new GLFWWindowHint(131074);
    public static final GLFWWindowHint GLFW_MAXIMIZED = new GLFWWindowHint(131080);
    public static final GLFWWindowHint GLFW_MOUSE_PASSTHROUGH = new GLFWWindowHint(131085);
    public static final GLFWWindowHint GLFW_POSITION_X = new GLFWWindowHint(131086);
    public static final GLFWWindowHint GLFW_POSITION_Y = new GLFWWindowHint(131087);
    public static final GLFWWindowHint GLFW_RESIZABLE = new GLFWWindowHint(131075);
    public static final GLFWWindowHint GLFW_TRANSPARENT_FRAMEBUFFER = new GLFWWindowHint(131082);
    public static final GLFWWindowHint GLFW_VISIBLE = new GLFWWindowHint(131076);

    public static GLFWWindowHint fromValue(int value) {
        return switch (value) {
            case 131078 -> GLFW_AUTO_ICONIFY;
            case 131077 -> GLFW_DECORATED;
            case 131079 -> GLFW_FLOATING;
            case 131073 -> GLFW_FOCUSED;
            case 131084 -> GLFW_FOCUS_ON_SHOW;
            case 131083 -> GLFW_HOVERED;
            case 131074 -> GLFW_ICONIFIED;
            case 131080 -> GLFW_MAXIMIZED;
            case 131085 -> GLFW_MOUSE_PASSTHROUGH;
            case 131086 -> GLFW_POSITION_X;
            case 131087 -> GLFW_POSITION_Y;
            case 131075 -> GLFW_RESIZABLE;
            case 131082 -> GLFW_TRANSPARENT_FRAMEBUFFER;
            case 131076 -> GLFW_VISIBLE;
            default -> new GLFWWindowHint(value);
        };
    }
}
