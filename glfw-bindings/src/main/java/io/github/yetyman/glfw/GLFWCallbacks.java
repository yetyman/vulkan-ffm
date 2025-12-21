package io.github.yetyman.glfw;

import io.github.yetyman.glfw.generated.*;
import java.lang.foreign.*;

/**
 * GLFW callback utilities for setting up native callbacks.
 */
public class GLFWCallbacks {
    
    /**
     * Sets a framebuffer size callback that will be called when the window's framebuffer is resized.
     */
    public static MemorySegment setFramebufferSizeCallback(MemorySegment window, 
                                                          FramebufferSizeCallback callback, 
                                                          Arena arena) {
        MemorySegment callbackStub = GLFWframebuffersizefun.allocate(callback::onResize, arena);
        return GLFWFFM.glfwSetFramebufferSizeCallback(window, callbackStub);
    }
    
    /**
     * Sets a key callback that will be called when a key is pressed, repeated or released.
     */
    public static MemorySegment setKeyCallback(MemorySegment window, 
                                             KeyCallback callback, 
                                             Arena arena) {
        MemorySegment callbackStub = GLFWkeyfun.allocate(callback::onKey, arena);
        return GLFWFFM.glfwSetKeyCallback(window, callbackStub);
    }
    
    /**
     * Sets a mouse button callback that will be called when a mouse button is pressed or released.
     */
    public static MemorySegment setMouseButtonCallback(MemorySegment window,
                                                      MouseButtonCallback callback,
                                                      Arena arena) {
        MemorySegment callbackStub = GLFWmousebuttonfun.allocate(callback::onMouseButton, arena);
        return GLFWFFM.glfwSetMouseButtonCallback(window, callbackStub);
    }
    
    /**
     * Sets a cursor position callback that will be called when the cursor is moved.
     */
    public static MemorySegment setCursorPosCallback(MemorySegment window,
                                                    CursorPosCallback callback,
                                                    Arena arena) {
        MemorySegment callbackStub = GLFWcursorposfun.allocate(callback::onCursorPos, arena);
        return GLFWFFM.glfwSetCursorPosCallback(window, callbackStub);
    }
    
    /**
     * Sets a scroll callback that will be called when the user scrolls.
     */
    public static MemorySegment setScrollCallback(MemorySegment window,
                                                 ScrollCallback callback,
                                                 Arena arena) {
        MemorySegment callbackStub = GLFWscrollfun.allocate(callback::onScroll, arena);
        return GLFWFFM.glfwSetScrollCallback(window, callbackStub);
    }
    
    // Callback interfaces
    @FunctionalInterface
    public interface FramebufferSizeCallback {
        void onResize(MemorySegment window, int width, int height);
    }
    
    @FunctionalInterface
    public interface KeyCallback {
        void onKey(MemorySegment window, int key, int scancode, int action, int mods);
    }
    
    @FunctionalInterface
    public interface MouseButtonCallback {
        void onMouseButton(MemorySegment window, int button, int action, int mods);
    }
    
    @FunctionalInterface
    public interface CursorPosCallback {
        void onCursorPos(MemorySegment window, double xpos, double ypos);
    }
    
    @FunctionalInterface
    public interface ScrollCallback {
        void onScroll(MemorySegment window, double xoffset, double yoffset);
    }
}