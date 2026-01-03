package io.github.yetyman.glfw.enums;

import java.util.*;

/**
 * Type-safe constants for GLFWPlatform
 * Generated from jextract bindings
 */
public record GLFWPlatform(int value) {

    public static final GLFWPlatform GLFW_ANY_PLATFORM = new GLFWPlatform(393216);
    public static final GLFWPlatform GLFW_PLATFORM_COCOA = new GLFWPlatform(393218);
    public static final GLFWPlatform GLFW_PLATFORM_NULL = new GLFWPlatform(393221);
    public static final GLFWPlatform GLFW_PLATFORM_WAYLAND = new GLFWPlatform(393219);
    public static final GLFWPlatform GLFW_PLATFORM_WIN32 = new GLFWPlatform(393217);
    public static final GLFWPlatform GLFW_PLATFORM_X11 = new GLFWPlatform(393220);

    public static GLFWPlatform fromValue(int value) {
        return switch (value) {
            case 393216 -> GLFW_ANY_PLATFORM;
            case 393218 -> GLFW_PLATFORM_COCOA;
            case 393221 -> GLFW_PLATFORM_NULL;
            case 393219 -> GLFW_PLATFORM_WAYLAND;
            case 393217 -> GLFW_PLATFORM_WIN32;
            case 393220 -> GLFW_PLATFORM_X11;
            default -> new GLFWPlatform(value);
        };
    }
}
