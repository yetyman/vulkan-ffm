package io.github.yetyman.glfw.enums;

import java.util.*;

/**
 * Type-safe constants for GLFWFramebuffer
 * Generated from jextract bindings
 */
public record GLFWFramebuffer(int value) {

    public static final GLFWFramebuffer GLFW_ACCUM_ALPHA_BITS = new GLFWFramebuffer(135178);
    public static final GLFWFramebuffer GLFW_ACCUM_BLUE_BITS = new GLFWFramebuffer(135177);
    public static final GLFWFramebuffer GLFW_ACCUM_GREEN_BITS = new GLFWFramebuffer(135176);
    public static final GLFWFramebuffer GLFW_ACCUM_RED_BITS = new GLFWFramebuffer(135175);
    public static final GLFWFramebuffer GLFW_ALPHA_BITS = new GLFWFramebuffer(135172);
    public static final GLFWFramebuffer GLFW_AUX_BUFFERS = new GLFWFramebuffer(135179);
    public static final GLFWFramebuffer GLFW_BLUE_BITS = new GLFWFramebuffer(135171);
    public static final GLFWFramebuffer GLFW_DEPTH_BITS = new GLFWFramebuffer(135173);
    public static final GLFWFramebuffer GLFW_DOUBLEBUFFER = new GLFWFramebuffer(135184);
    public static final GLFWFramebuffer GLFW_GREEN_BITS = new GLFWFramebuffer(135170);
    public static final GLFWFramebuffer GLFW_RED_BITS = new GLFWFramebuffer(135169);
    public static final GLFWFramebuffer GLFW_REFRESH_RATE = new GLFWFramebuffer(135183);
    public static final GLFWFramebuffer GLFW_SAMPLES = new GLFWFramebuffer(135181);
    public static final GLFWFramebuffer GLFW_SRGB_CAPABLE = new GLFWFramebuffer(135182);
    public static final GLFWFramebuffer GLFW_STENCIL_BITS = new GLFWFramebuffer(135174);
    public static final GLFWFramebuffer GLFW_STEREO = new GLFWFramebuffer(135180);

    public static GLFWFramebuffer fromValue(int value) {
        return switch (value) {
            case 135178 -> GLFW_ACCUM_ALPHA_BITS;
            case 135177 -> GLFW_ACCUM_BLUE_BITS;
            case 135176 -> GLFW_ACCUM_GREEN_BITS;
            case 135175 -> GLFW_ACCUM_RED_BITS;
            case 135172 -> GLFW_ALPHA_BITS;
            case 135179 -> GLFW_AUX_BUFFERS;
            case 135171 -> GLFW_BLUE_BITS;
            case 135173 -> GLFW_DEPTH_BITS;
            case 135184 -> GLFW_DOUBLEBUFFER;
            case 135170 -> GLFW_GREEN_BITS;
            case 135169 -> GLFW_RED_BITS;
            case 135183 -> GLFW_REFRESH_RATE;
            case 135181 -> GLFW_SAMPLES;
            case 135182 -> GLFW_SRGB_CAPABLE;
            case 135174 -> GLFW_STENCIL_BITS;
            case 135180 -> GLFW_STEREO;
            default -> new GLFWFramebuffer(value);
        };
    }
}
