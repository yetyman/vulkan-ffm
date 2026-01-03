package io.github.yetyman.glfw.enums;

import java.util.*;

/**
 * Type-safe constants for GLFWError
 * Generated from jextract bindings
 */
public record GLFWError(int value) {

    public static final GLFWError GLFW_API_UNAVAILABLE = new GLFWError(65542);
    public static final GLFWError GLFW_CONTEXT_NO_ERROR = new GLFWError(139274);
    public static final GLFWError GLFW_CURSOR_UNAVAILABLE = new GLFWError(65547);
    public static final GLFWError GLFW_FEATURE_UNAVAILABLE = new GLFWError(65548);
    public static final GLFWError GLFW_FORMAT_UNAVAILABLE = new GLFWError(65545);
    public static final GLFWError GLFW_NO_ERROR = new GLFWError(0);
    public static final GLFWError GLFW_PLATFORM_ERROR = new GLFWError(65544);
    public static final GLFWError GLFW_PLATFORM_UNAVAILABLE = new GLFWError(65550);
    public static final GLFWError GLFW_VERSION_UNAVAILABLE = new GLFWError(65543);

    public static GLFWError fromValue(int value) {
        return switch (value) {
            case 65542 -> GLFW_API_UNAVAILABLE;
            case 139274 -> GLFW_CONTEXT_NO_ERROR;
            case 65547 -> GLFW_CURSOR_UNAVAILABLE;
            case 65548 -> GLFW_FEATURE_UNAVAILABLE;
            case 65545 -> GLFW_FORMAT_UNAVAILABLE;
            case 0 -> GLFW_NO_ERROR;
            case 65544 -> GLFW_PLATFORM_ERROR;
            case 65550 -> GLFW_PLATFORM_UNAVAILABLE;
            case 65543 -> GLFW_VERSION_UNAVAILABLE;
            default -> new GLFWError(value);
        };
    }
}
