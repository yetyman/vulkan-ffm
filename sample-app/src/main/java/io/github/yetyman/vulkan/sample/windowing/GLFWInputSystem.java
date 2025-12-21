package io.github.yetyman.vulkan.sample.windowing;

import io.github.yetyman.vulkan.input.*;
import io.github.yetyman.vulkan.input.events.*;
import io.github.yetyman.glfw.GLFWCallbacks;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.function.Consumer;

public class GLFWInputSystem implements InputSystem {
    
    @Override
    public void setInputCallback(MemorySegment window, Consumer<InputEvent> callback, Arena arena) {
        // Register all GLFW callbacks, convert to unified events
        GLFWCallbacks.setKeyCallback(window, 
            (w, key, scancode, action, mods) -> 
                callback.accept(new KeyEvent(key, scancode, action, mods, System.nanoTime())), arena);
                
        GLFWCallbacks.setMouseButtonCallback(window,
            (w, button, action, mods) ->
                callback.accept(new MouseButtonEvent(button, action, mods, System.nanoTime())), arena);
                
        GLFWCallbacks.setCursorPosCallback(window,
            (w, x, y) ->
                callback.accept(new MouseMoveEvent(x, y, System.nanoTime())), arena);
                
        GLFWCallbacks.setScrollCallback(window,
            (w, xOffset, yOffset) ->
                callback.accept(new ScrollEvent(xOffset, yOffset, System.nanoTime())), arena);
    }
}