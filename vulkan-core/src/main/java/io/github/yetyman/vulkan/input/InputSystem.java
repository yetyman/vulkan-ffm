package io.github.yetyman.vulkan.input;

import io.github.yetyman.vulkan.input.events.InputEvent;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.function.Consumer;

public interface InputSystem {
    void setInputCallback(MemorySegment window, Consumer<InputEvent> callback, Arena arena);
}