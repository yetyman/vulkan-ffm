package io.github.yetyman.glfw.enums;

import java.util.*;

/**
 * Type-safe constants for GLFWClientAPI
 * Generated from jextract bindings
 */
public record GLFWClientAPI(int value) {

    public static final GLFWClientAPI GLFW_CLIENT_API = new GLFWClientAPI(139265);
    public static final GLFWClientAPI GLFW_CONTEXT_CREATION_API = new GLFWClientAPI(139275);
    public static final GLFWClientAPI GLFW_EGL_CONTEXT_API = new GLFWClientAPI(221186);
    public static final GLFWClientAPI GLFW_NATIVE_CONTEXT_API = new GLFWClientAPI(221185);
    public static final GLFWClientAPI GLFW_NO_API = new GLFWClientAPI(0);
    public static final GLFWClientAPI GLFW_OPENGL_API = new GLFWClientAPI(196609);
    public static final GLFWClientAPI GLFW_OPENGL_ES_API = new GLFWClientAPI(196610);
    public static final GLFWClientAPI GLFW_OSMESA_CONTEXT_API = new GLFWClientAPI(221187);

    public static GLFWClientAPI fromValue(int value) {
        return switch (value) {
            case 139265 -> GLFW_CLIENT_API;
            case 139275 -> GLFW_CONTEXT_CREATION_API;
            case 221186 -> GLFW_EGL_CONTEXT_API;
            case 221185 -> GLFW_NATIVE_CONTEXT_API;
            case 0 -> GLFW_NO_API;
            case 196609 -> GLFW_OPENGL_API;
            case 196610 -> GLFW_OPENGL_ES_API;
            case 221187 -> GLFW_OSMESA_CONTEXT_API;
            default -> new GLFWClientAPI(value);
        };
    }
}
