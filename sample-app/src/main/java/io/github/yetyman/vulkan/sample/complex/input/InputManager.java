package io.github.yetyman.vulkan.sample.complex.input;

import java.lang.foreign.MemorySegment;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import io.github.yetyman.glfw.GLFWCallbacks;

import java.lang.foreign.Arena;

public class InputManager implements AutoCloseable {
    private final ConcurrentLinkedQueue<InputEvent> inputQueue = new ConcurrentLinkedQueue<>();
    private final Thread inputThread;
    private volatile boolean running = true;
    
    private final ConcurrentHashMap<Long, InputHandler> handlers = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);
    
    public InputManager(MemorySegment window) {
        GLFWCallbacks.setKeyCallback(window, this::queueEvent, Arena.global());
        inputThread = new Thread(this::inputThreadLoop, "InputThread");
        inputThread.setDaemon(true);
        inputThread.start();
    }
    
    private void queueEvent(MemorySegment window, int key, int scancode, int action, int mods) {
        inputQueue.offer(new InputEvent(key, scancode, action, mods));
    }
    
    public long registerHandler(Predicate<InputEvent> filter, Runnable callback) {
        long id = nextId.getAndIncrement();
        handlers.put(id, new InputHandler(id, filter, callback));
        return id;
    }
    
    public void unregisterHandler(long id) {
        handlers.remove(id);
    }

    private void inputThreadLoop() {
        while (running) {
            processInputEvents();
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    private void processInputEvents() {
        InputEvent event;
        while ((event = inputQueue.poll()) != null) {
            long now = System.nanoTime();
            
            for (InputHandler handler : handlers.values()) {
                if (handler.filter.test(event)) {
                    // 200ms debounce per handler
                    if (now - handler.lastTrigger > 200_000_000L) {
                        handler.callback.run();
                        handler.lastTrigger = now;
                    }
                }
            }
        }
    }
    
    @Override
    public void close() {
        running = false;
        if (inputThread != null) {
            try {
                inputThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}