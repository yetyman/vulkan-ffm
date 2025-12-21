package io.github.yetyman.vulkan.input;

import io.github.yetyman.vulkan.input.events.InputEvent;

import java.util.function.Predicate;

public class InputHandler {
    public final long id;
    public final Predicate<InputEvent> filter;
    public final Runnable callback;
    public long lastTrigger = 0;
    
    public InputHandler(long id, Predicate<InputEvent> filter, Runnable callback) {
        this.id = id;
        this.filter = filter;
        this.callback = callback;
    }
}