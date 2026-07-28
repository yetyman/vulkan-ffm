package io.github.yetyman.vulkan;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

public interface WindowSystem {

    @FunctionalInterface
    interface ResizeCallback {
        void onResize(MemorySegment window, int width, int height);
    }

    // Window lifecycle
    MemorySegment createWindow(int width, int height, String title);

    void destroyWindow(MemorySegment window);

    void terminate();

    // Vulkan integration
    String[] getRequiredVulkanExtensions(Arena arena);

    MemorySegment createSurface(MemorySegment instance, MemorySegment window, Arena arena);

    // Event handling
    boolean shouldClose(MemorySegment window);

    void pollEvents();

    /**
     * Waits for events with a timeout. Blocks until an event arrives or the timeout
     * (in seconds) expires, whichever comes first. On input arrival, wakes instantly.
     * Prefer this over pollEvents() + sleep for power-efficient event loops with
     * continuous animation (use the timeout as the animation floor rate).
     */
    void waitEventsTimeout(double timeoutSeconds);

    void setResizeCallback(MemorySegment window, ResizeCallback callback, Arena arena);

    // Window properties
    void getFramebufferSize(MemorySegment window, MemorySegment widthPtr, MemorySegment heightPtr);

    void setResizable(boolean resizable);
}