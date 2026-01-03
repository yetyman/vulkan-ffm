package io.github.yetyman.glfw.enums;

import java.util.*;

/**
 * Type-safe constants for GLFWOpenGLProfile
 * Generated from jextract bindings
 */
public record GLFWOpenGLProfile(int value) {

    public static final GLFWOpenGLProfile GLFW_OPENGL_ANY_PROFILE = new GLFWOpenGLProfile(0);
    public static final GLFWOpenGLProfile GLFW_OPENGL_COMPAT_PROFILE = new GLFWOpenGLProfile(204802);
    public static final GLFWOpenGLProfile GLFW_OPENGL_CORE_PROFILE = new GLFWOpenGLProfile(204801);

    public static GLFWOpenGLProfile fromValue(int value) {
        return switch (value) {
            case 0 -> GLFW_OPENGL_ANY_PROFILE;
            case 204802 -> GLFW_OPENGL_COMPAT_PROFILE;
            case 204801 -> GLFW_OPENGL_CORE_PROFILE;
            default -> new GLFWOpenGLProfile(value);
        };
    }
}
