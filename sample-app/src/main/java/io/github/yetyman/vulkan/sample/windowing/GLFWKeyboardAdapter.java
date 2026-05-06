package io.github.yetyman.vulkan.sample.windowing;

import io.github.yetyman.glfw.GLFWCallbacks;
import io.github.yetyman.glfw.enums.GLFWAction;
import io.github.yetyman.structures.input.KeyboardState;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.function.IntFunction;

public class GLFWKeyboardAdapter {

    public GLFWKeyboardAdapter(MemorySegment window, KeyboardState sink,
                               IntFunction<String> keyNameResolver, Arena arena) {
        GLFWCallbacks.setKeyCallback(window, (w, key, scancode, action, mods) -> {
            if (action == GLFWAction.GLFW_REPEAT.value()) return;
            String name = keyNameResolver.apply(key);
            if (name == null) return;
            sink.updateKey(name, action == GLFWAction.GLFW_PRESS.value(), System.nanoTime());
        }, arena);
    }
}
