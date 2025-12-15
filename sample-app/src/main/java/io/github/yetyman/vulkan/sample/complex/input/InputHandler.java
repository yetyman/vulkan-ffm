package io.github.yetyman.vulkan.sample.complex.input;

import java.util.function.Predicate;

class InputHandler {
    final long id;
    final Predicate<InputEvent> filter;
    final Runnable callback;
    volatile long lastTrigger;

    InputHandler(long id, Predicate<InputEvent> filter, Runnable callback) {
        this.id = id;
        this.filter = filter;
        this.callback = callback;
        this.lastTrigger = 0;
    }
}
