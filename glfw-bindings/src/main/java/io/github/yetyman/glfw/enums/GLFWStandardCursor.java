package io.github.yetyman.glfw.enums;

import java.util.*;

/**
 * Type-safe constants for GLFWStandardCursor
 * Generated from jextract bindings
 */
public record GLFWStandardCursor(int value) {

    public static final GLFWStandardCursor GLFW_ARROW_CURSOR = new GLFWStandardCursor(221185);
    public static final GLFWStandardCursor GLFW_CENTER_CURSOR = new GLFWStandardCursor(131081);
    public static final GLFWStandardCursor GLFW_CROSSHAIR_CURSOR = new GLFWStandardCursor(221187);
    public static final GLFWStandardCursor GLFW_HAND_CURSOR = new GLFWStandardCursor(221188);
    public static final GLFWStandardCursor GLFW_HRESIZE_CURSOR = new GLFWStandardCursor(221189);
    public static final GLFWStandardCursor GLFW_IBEAM_CURSOR = new GLFWStandardCursor(221186);
    public static final GLFWStandardCursor GLFW_NOT_ALLOWED_CURSOR = new GLFWStandardCursor(221194);
    public static final GLFWStandardCursor GLFW_POINTING_HAND_CURSOR = GLFW_HAND_CURSOR;
    public static final GLFWStandardCursor GLFW_RESIZE_ALL_CURSOR = new GLFWStandardCursor(221193);
    public static final GLFWStandardCursor GLFW_RESIZE_EW_CURSOR = GLFW_HRESIZE_CURSOR;
    public static final GLFWStandardCursor GLFW_RESIZE_NESW_CURSOR = new GLFWStandardCursor(221192);
    public static final GLFWStandardCursor GLFW_RESIZE_NS_CURSOR = new GLFWStandardCursor(221190);
    public static final GLFWStandardCursor GLFW_RESIZE_NWSE_CURSOR = new GLFWStandardCursor(221191);
    public static final GLFWStandardCursor GLFW_VRESIZE_CURSOR = GLFW_RESIZE_NS_CURSOR;

    public static GLFWStandardCursor fromValue(int value) {
        return switch (value) {
            case 221185 -> GLFW_ARROW_CURSOR;
            case 131081 -> GLFW_CENTER_CURSOR;
            case 221187 -> GLFW_CROSSHAIR_CURSOR;
            case 221188 -> GLFW_HAND_CURSOR;
            case 221189 -> GLFW_HRESIZE_CURSOR;
            case 221186 -> GLFW_IBEAM_CURSOR;
            case 221194 -> GLFW_NOT_ALLOWED_CURSOR;
            case 221193 -> GLFW_RESIZE_ALL_CURSOR;
            case 221192 -> GLFW_RESIZE_NESW_CURSOR;
            case 221190 -> GLFW_VRESIZE_CURSOR;
            case 221191 -> GLFW_RESIZE_NWSE_CURSOR;
            default -> new GLFWStandardCursor(value);
        };
    }
}
