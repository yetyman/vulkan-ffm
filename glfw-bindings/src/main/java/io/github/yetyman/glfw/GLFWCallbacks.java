package io.github.yetyman.glfw;

import io.github.yetyman.glfw.generated.*;
import java.lang.foreign.*;

/**
 * GLFW callback utilities for setting up native callbacks.
 */
public class GLFWCallbacks {
    
    /**
     * Sets a framebuffer size callback that will be called when the window's framebuffer is resized.
     * @param window the GLFW window
     * @param callback the callback function (window, width, height) -> void
     * @param arena arena for callback allocation
     * @return the previous callback or null
     */
    public static MemorySegment setFramebufferSizeCallback(MemorySegment window, 
                                                          FramebufferSizeCallback callback, 
                                                          Arena arena) {
        MemorySegment callbackStub = GLFWframebuffersizefun.allocate(callback::onResize, arena);
        return GLFWFFM.glfwSetFramebufferSizeCallback(window, callbackStub);
    }
    
    /**
     * Functional interface for framebuffer size callbacks.
     */
    @FunctionalInterface
    public interface FramebufferSizeCallback {
        void onResize(MemorySegment window, int width, int height);
    }
}