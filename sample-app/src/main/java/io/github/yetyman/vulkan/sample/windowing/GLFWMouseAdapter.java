package io.github.yetyman.vulkan.sample.windowing;

import io.github.yetyman.glfw.GLFWCallbacks;
import io.github.yetyman.structures.input.MouseState;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

public class GLFWMouseAdapter {

    public GLFWMouseAdapter(MemorySegment window, MouseState sink, Arena arena) {
        GLFWCallbacks.setCursorPosCallback(window,
                (w, x, y) -> sink.updatePosition(x, y, System.nanoTime()), arena);
        GLFWCallbacks.setMouseButtonCallback(window,
                (w, button, action, mods) -> sink.updateButton(button, action != 0, System.nanoTime()), arena);
        GLFWCallbacks.setScrollCallback(window,
                (w, dx, dy) -> sink.updateScroll(dx, dy, System.nanoTime()), arena);
    }
}
